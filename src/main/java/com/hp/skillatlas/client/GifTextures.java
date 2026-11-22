package com.hp.skillatlas.client;

import com.hp.skillatlas.SkillAtlas;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Util;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 客户端 GIF 动态贴图管理器：异步解码 GIF 帧并按时间上传至单张 DynamicTexture。
 */
public final class GifTextures {
    private static final int MAX_CACHED = 8;
    private static final int MAX_FRAMES = 300;
    private static final int MAX_DIMENSION = 1024;
    private static final long MAX_TOTAL_PIXELS = 48L * 1024L * 1024L;
    private static final int DEFAULT_DELAY_MS = 100;

    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SkillAtlas-GifDecoder");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static final AtomicInteger TEXTURE_SEQ = new AtomicInteger();
    private static final Map<Identifier, int[]> SIZE_CACHE = new HashMap<>();
    private static final LinkedHashMap<Identifier, GifEntry> CACHE = new LinkedHashMap<>(16, 0.75F, true);

    private GifTextures() {
    }

    public static boolean isGif(Identifier id) {
        return id != null && id.getPath().toLowerCase(Locale.ROOT).endsWith(".gif");
    }

    /**
     * 同步读取 GIF 逻辑尺寸（仅读取前 10 字节文件头，不触发整图解码）。
     */
    public static int[] size(Identifier id) {
        if (!isGif(id)) {
            return new int[]{0, 0};
        }
        return SIZE_CACHE.computeIfAbsent(id, GifTextures::readHeaderSize);
    }

    /**
     * 解析贴图：静态贴图原样返回；GIF 返回当前帧对应的动态纹理 ID，解码中或失败时返回 null。
     */
    public static Identifier resolve(Identifier id) {
        if (id == null) {
            return null;
        }
        if (!isGif(id)) {
            return id;
        }
        GifEntry entry = CACHE.get(id);
        if (entry == null) {
            entry = new GifEntry(id, GENERATION.get());
            CACHE.put(id, entry);
            evictIfNeeded();
            startDecode(entry);
        }
        return entry.currentTexture();
    }

    /**
     * 清空缓存并释放所有已注册的 GIF 动态纹理。
     */
    public static void clear() {
        GENERATION.incrementAndGet();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        Runnable task = () -> {
            for (GifEntry entry : CACHE.values()) {
                entry.release();
            }
            CACHE.clear();
            SIZE_CACHE.clear();
        };
        if (mc.isSameThread()) {
            task.run();
        } else {
            mc.execute(task);
        }
    }

    private static void evictIfNeeded() {
        while (CACHE.size() > MAX_CACHED) {
            Iterator<Map.Entry<Identifier, GifEntry>> it = CACHE.entrySet().iterator();
            if (!it.hasNext()) {
                break;
            }
            Map.Entry<Identifier, GifEntry> oldest = it.next();
            oldest.getValue().release();
            it.remove();
        }
    }

    private static int[] readHeaderSize(Identifier id) {
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
        if (res.isEmpty()) {
            SkillAtlas.LOGGER.warn("Skill atlas GIF texture not found: {}", id);
            return new int[]{0, 0};
        }
        try (InputStream in = res.get().open()) {
            byte[] head = in.readNBytes(10);
            if (head.length == 10
                    && head[0] == 'G' && head[1] == 'I' && head[2] == 'F'
                    && head[3] == '8' && (head[4] == '7' || head[4] == '9') && head[5] == 'a') {
                int w = (head[6] & 0xFF) | ((head[7] & 0xFF) << 8);
                int h = (head[8] & 0xFF) | ((head[9] & 0xFF) << 8);
                if (w > 0 && h > 0) {
                    return new int[]{w, h};
                }
            }
            SkillAtlas.LOGGER.warn("Invalid GIF header for texture: {}", id);
        } catch (IOException e) {
            SkillAtlas.LOGGER.warn("Failed to read GIF header {}: {}", id, e.getMessage());
        }
        return new int[]{0, 0};
    }

    private static void startDecode(GifEntry entry) {
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(entry.source);
        if (res.isEmpty()) {
            entry.failed = true;
            return;
        }
        byte[] bytes;
        try (InputStream in = res.get().open()) {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            SkillAtlas.LOGGER.warn("Failed to read GIF bytes {}: {}", entry.source, e.getMessage());
            entry.failed = true;
            return;
        }
        int gen = entry.generation;
        DECODER.execute(() -> {
            if (gen != GENERATION.get()) {
                return;
            }
            try {
                Decoded decoded = decode(entry.source, bytes);
                if (gen != GENERATION.get()) {
                    return;
                }
                if (decoded == null || decoded.frames.length == 0) {
                    entry.failed = true;
                } else {
                    entry.decoded = decoded;
                }
            } catch (Throwable t) {
                SkillAtlas.LOGGER.warn("Failed to decode GIF texture {}: {}", entry.source, t.toString());
                entry.failed = true;
            }
        });
    }

    private static Decoded decode(Identifier id, byte[] bytes) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            SkillAtlas.LOGGER.warn("No ImageIO GIF reader available for {}", id);
            return null;
        }
        ImageReader reader = readers.next();
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(iis, false);
            int total = reader.getNumImages(true);
            if (total <= 0) {
                return null;
            }
            int frameCount = Math.min(total, MAX_FRAMES);
            if (total > MAX_FRAMES) {
                SkillAtlas.LOGGER.warn("GIF {} has {} frames, truncating to {}", id, total, MAX_FRAMES);
            }

            int canvasW = 0;
            int canvasH = 0;
            IIOMetadata streamMeta = reader.getStreamMetadata();
            if (streamMeta != null) {
                int[] logical = parseLogicalSize(streamMeta);
                canvasW = logical[0];
                canvasH = logical[1];
            }

            BufferedImage first = reader.read(0);
            if (canvasW <= 0 || canvasH <= 0) {
                canvasW = first.getWidth();
                canvasH = first.getHeight();
            }
            if (canvasW <= 0 || canvasH <= 0) {
                return null;
            }

            // 计算安全输出分辨率，避免超大动图导致内存溢出
            double scale = 1.0D;
            int maxSide = Math.max(canvasW, canvasH);
            if (maxSide > MAX_DIMENSION) {
                scale = Math.min(scale, (double) MAX_DIMENSION / maxSide);
            }
            long estimatedPixels = (long) frameCount * canvasW * canvasH;
            if (estimatedPixels > MAX_TOTAL_PIXELS) {
                scale = Math.min(scale, Math.sqrt((double) MAX_TOTAL_PIXELS / estimatedPixels));
            }
            int outW = Math.max(1, (int) Math.round(canvasW * scale));
            int outH = Math.max(1, (int) Math.round(canvasH * scale));
            boolean needScale = outW != canvasW || outH != canvasH;

            BufferedImage master = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
            BufferedImage scaled = needScale ? new BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB) : null;
            List<int[]> framePixels = new ArrayList<>(frameCount);
            int[] delays = new int[frameCount];
            long totalDuration = 0L;

            for (int i = 0; i < frameCount; i++) {
                BufferedImage raw = (i == 0) ? first : reader.read(i);
                FrameMeta meta = parseFrameMeta(reader.getImageMetadata(i), raw.getWidth(), raw.getHeight());
                BufferedImage backup = null;
                if ("restoreToPrevious".equals(meta.disposal)) {
                    backup = copyImage(master);
                }

                Graphics2D g = master.createGraphics();
                g.setComposite(AlphaComposite.SrcOver);
                g.drawImage(raw, meta.left, meta.top, null);
                g.dispose();

                BufferedImage sourceForPixels = master;
                if (needScale) {
                    Graphics2D sg = scaled.createGraphics();
                    sg.setComposite(AlphaComposite.Src);
                    sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    sg.drawImage(master, 0, 0, outW, outH, null);
                    sg.dispose();
                    sourceForPixels = scaled;
                }

                framePixels.add(sourceForPixels.getRGB(0, 0, outW, outH, null, 0, outW));
                delays[i] = meta.delayMs;
                totalDuration += meta.delayMs;

                if ("restoreToBackgroundColor".equals(meta.disposal)) {
                    Graphics2D cg = master.createGraphics();
                    cg.setComposite(AlphaComposite.Clear);
                    cg.setBackground(new Color(0, 0, 0, 0));
                    cg.clearRect(meta.left, meta.top, meta.width, meta.height);
                    cg.dispose();
                } else if ("restoreToPrevious".equals(meta.disposal) && backup != null) {
                    Graphics2D rg = master.createGraphics();
                    rg.setComposite(AlphaComposite.Src);
                    rg.drawImage(backup, 0, 0, null);
                    rg.dispose();
                }
            }

            return new Decoded(outW, outH, framePixels.toArray(new int[0][]), delays, Math.max(1L, totalDuration));
        } finally {
            reader.dispose();
        }
    }

    private static BufferedImage copyImage(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    private static int[] parseLogicalSize(IIOMetadata streamMeta) {
        try {
            Node root = streamMeta.getAsTree("javax_imageio_gif_stream_1.0");
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if ("LogicalScreenDescriptor".equals(child.getNodeName())) {
                    NamedNodeMap attrs = child.getAttributes();
                    int w = parseAttrInt(attrs, "logicalScreenWidth", 0);
                    int h = parseAttrInt(attrs, "logicalScreenHeight", 0);
                    return new int[]{w, h};
                }
            }
        } catch (Exception ignored) {
        }
        return new int[]{0, 0};
    }

    private static FrameMeta parseFrameMeta(IIOMetadata meta, int fallbackW, int fallbackH) {
        int left = 0;
        int top = 0;
        int width = fallbackW;
        int height = fallbackH;
        int delayMs = DEFAULT_DELAY_MS;
        String disposal = "none";
        if (meta != null) {
            try {
                Node root = meta.getAsTree("javax_imageio_gif_image_1.0");
                NodeList children = root.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);
                    String name = child.getNodeName();
                    NamedNodeMap attrs = child.getAttributes();
                    if ("ImageDescriptor".equals(name)) {
                        left = parseAttrInt(attrs, "imageLeftPosition", 0);
                        top = parseAttrInt(attrs, "imageTopPosition", 0);
                        width = parseAttrInt(attrs, "imageWidth", fallbackW);
                        height = parseAttrInt(attrs, "imageHeight", fallbackH);
                    } else if ("GraphicControlExtension".equals(name)) {
                        int delayCs = parseAttrInt(attrs, "delayTime", 10);
                        delayMs = delayCs <= 1 ? DEFAULT_DELAY_MS : delayCs * 10;
                        Node d = attrs.getNamedItem("disposalMethod");
                        if (d != null && d.getNodeValue() != null) {
                            disposal = d.getNodeValue();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return new FrameMeta(left, top, width, height, delayMs, disposal);
    }

    private static int parseAttrInt(NamedNodeMap attrs, String name, int fallback) {
        if (attrs == null) {
            return fallback;
        }
        Node node = attrs.getNamedItem(name);
        if (node == null || node.getNodeValue() == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(node.getNodeValue());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private record FrameMeta(int left, int top, int width, int height, int delayMs, String disposal) {
    }

    private record Decoded(int width, int height, int[][] frames, int[] delaysMs, long totalDurationMs) {
    }

    private static final class GifEntry {
        final Identifier source;
        final int generation;
        volatile Decoded decoded;
        volatile boolean failed;

        private Identifier dynamicId;
        private DynamicTexture texture;
        private int uploadedFrame = -1;
        private long startTimeMs = -1L;

        GifEntry(Identifier source, int generation) {
            this.source = source;
            this.generation = generation;
        }

        Identifier currentTexture() {
            if (failed) {
                return null;
            }
            Decoded d = decoded;
            if (d == null) {
                return null;
            }
            if (texture == null) {
                int seq = TEXTURE_SEQ.incrementAndGet();
                texture = new DynamicTexture(() -> "skill_atlas_gif_" + seq, d.width, d.height, false);
                dynamicId = SkillAtlas.id("dynamic/gif/" + seq);
                Minecraft.getInstance().getTextureManager().register(dynamicId, texture);
                startTimeMs = Util.getMillis();
                uploadedFrame = -1;
            }
            int frameIndex = pickFrame(d);
            if (frameIndex != uploadedFrame) {
                uploadFrame(d, frameIndex);
                uploadedFrame = frameIndex;
            }
            return dynamicId;
        }

        private int pickFrame(Decoded d) {
            if (d.frames.length <= 1) {
                return 0;
            }
            long elapsed = Math.floorMod(Util.getMillis() - startTimeMs, d.totalDurationMs);
            long acc = 0L;
            for (int i = 0; i < d.delaysMs.length; i++) {
                acc += d.delaysMs[i];
                if (elapsed < acc) {
                    return i;
                }
            }
            return d.frames.length - 1;
        }

        private void uploadFrame(Decoded d, int index) {
            NativeImage pixels = texture.getPixels();
            if (pixels == null) {
                return;
            }
            int[] data = d.frames[index];
            int w = d.width;
            int h = d.height;
            for (int y = 0; y < h; y++) {
                int row = y * w;
                for (int x = 0; x < w; x++) {
                    pixels.setPixel(x, y, data[row + x]);
                }
            }
            texture.upload();
        }

        void release() {
            if (dynamicId != null) {
                Minecraft.getInstance().getTextureManager().release(dynamicId);
                dynamicId = null;
            }
            texture = null;
            uploadedFrame = -1;
            decoded = null;
        }
    }
}
