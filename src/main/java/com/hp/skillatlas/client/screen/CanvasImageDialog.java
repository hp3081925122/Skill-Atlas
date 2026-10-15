package com.hp.skillatlas.client.screen;

import com.google.gson.JsonArray;
import com.hp.skillatlas.client.ClientAtlas;
import com.hp.skillatlas.client.Names;
import com.hp.skillatlas.client.ui.Button;
import com.hp.skillatlas.client.ui.Dialog;
import com.hp.skillatlas.client.ui.Form;
import com.hp.skillatlas.client.ui.Gfx;
import com.hp.skillatlas.client.ui.ScrollGroup;
import com.hp.skillatlas.client.ui.Sounds;
import com.hp.skillatlas.client.ui.TextInput;
import com.hp.skillatlas.client.ui.Theme;
import com.hp.skillatlas.client.ui.Ui;
import com.hp.skillatlas.client.ui.Widget;
import com.hp.skillatlas.data.CanvasImage;
import com.hp.skillatlas.data.Category;
import com.hp.skillatlas.data.EditOps;
import com.hp.skillatlas.data.Json;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

public final class CanvasImageDialog extends Dialog {
    private final MainLayer main;
    private final String category;
    private final CanvasImage image;
    private final boolean create;
    private final List<TextInput> inputs = new ArrayList<>();
    private boolean saving;
    private Button save;

    public static void create(MainLayer main, int x, int y) {
        Category c = ClientAtlas.definition.categories.get(ClientAtlas.category);
        if (c == null) {
            main.error(Component.translatable("skill_atlas.ui.need_category"));
            return;
        }
        if (c.images.size() >= Category.MAX_IMAGES) {
            main.error(Component.translatable("skill_atlas.ui.image.limit", Category.MAX_IMAGES));
            return;
        }
        Set<String> ids = new HashSet<>();
        c.images.forEach(picture -> ids.add(picture.id));
        CanvasImage image = new CanvasImage();
        image.id = EditActions.freshId("image", ids);
        image.x = x;
        image.y = y;
        main.stack.push(new CanvasImageDialog(main, c.id, image, true));
    }

    public CanvasImageDialog(MainLayer main, String category, CanvasImage image, boolean create) {
        super(Component.translatable(create ? "skill_atlas.ui.image.add" : "skill_atlas.ui.image.edit"), 340, 300);
        this.main = main;
        this.category = category;
        this.image = image.copy();
        this.create = create;
    }

    @Override
    protected void build() {
        inputs.clear();
        ScrollGroup scroll = add(new ScrollGroup());
        scroll.bounds(contentX(), contentY(), contentW(), contentH());
        Form f = new Form(scroll, 64);
        TextInput texture = new TextInput(image.texture).max(256);
        texture.valid = value -> !value.isBlank() && Json.validResource(Json.normalizeResource(value));
        texture.onChange = value -> image.texture = value.trim();
        inputs.add(texture);
        Button pick = new Button(Component.translatable("skill_atlas.ui.pick"), () -> stack.push(PickerDialog.textures(path -> {
            image.texture = path;
            texture.setText(path);
            if (create) {
                int[] size = main.canvas.textureSize(ResourceLocation.tryParse(path));
                if (size[0] > 0 && size[1] > 0) {
                    double scale = Math.min(1, 256.0D / Math.max(size[0], size[1]));
                    image.width = Math.max(1, (int) Math.round(size[0] * scale));
                    image.height = Math.max(1, (int) Math.round(size[1] * scale));
                    Ui.focus(null);
                    layout(stack.width, stack.height);
                }
            }
        })));
        f.cells(Component.translatable("skill_atlas.ui.image.texture"), new float[]{7, 2.4F}, texture, pick);
        number(f, "skill_atlas.ui.image.x", image.x, -1_000_000, 1_000_000, value -> image.x = value);
        number(f, "skill_atlas.ui.image.y", image.y, -1_000_000, 1_000_000, value -> image.y = value);
        number(f, "skill_atlas.ui.image.width", image.width, 1, CanvasImage.MAX_SIZE, value -> image.width = value);
        number(f, "skill_atlas.ui.image.height", image.height, 1, CanvasImage.MAX_SIZE, value -> image.height = value);
        Widget preview = new Widget() {
            @Override
            public void render(float pt) {
                ResourceLocation path = ResourceLocation.tryParse(image.texture);
                if (path != null && main.canvas.textureSize(path)[0] > 0) {
                    float scale = Math.min((w - 8) / image.width, (h - 8) / image.height);
                    float width = image.width * scale;
                    float height = image.height * scale;
                    Gfx.texture(path, x + (w - width) / 2, y + (h - height) / 2, width, height, 0xFFFFFFFF);
                }
            }
        };
        f.full(preview, 80);
        NodeEditorDialog.hint(f, Component.translatable("skill_atlas.ui.image.hint"), Theme.DIM);
        f.finish();
        save = new Button(Component.translatable("skill_atlas.ui.save"), this::save).style(Button.Style.PRIMARY);
        save.enabled = !saving;
        footer(new Button(Component.translatable("skill_atlas.ui.cancel"), this::close), save);
    }

    private void number(Form form, String label, int value, int min, int max, IntConsumer setter) {
        TextInput input = new TextInput(String.valueOf(value)).max(10).filter(text -> text.matches("-?\\d*"));
        input.valid = text -> NodeEditorDialog.parseInt(text, min, max) != null;
        input.onChange = text -> {
            Integer parsed = NodeEditorDialog.parseInt(text, min, max);
            if (parsed != null) {
                setter.accept(parsed);
            }
        };
        inputs.add(form.row(Component.translatable(label), input));
    }

    private void save() {
        if (inputs.stream().anyMatch(input -> input.valid != null && !input.valid.test(input.text()))) {
            setStatus(Component.translatable("skill_atlas.ui.invalid_fields"), Theme.RED);
            Sounds.error();
            return;
        }
        if (main.canvas.textureSize(ResourceLocation.tryParse(image.texture))[0] <= 0) {
            setStatus(Component.translatable("skill_atlas.ui.image.missing_texture"), Theme.RED);
            return;
        }
        Category current = ClientAtlas.definition.categories.get(category);
        if (current == null) {
            setStatus(Component.translatable("skill_atlas.ui.need_category"), Theme.RED);
            return;
        }
        Category next = current.copy();
        image.texture = Json.normalizeResource(image.texture);
        if (create) {
            if (next.images.size() >= Category.MAX_IMAGES) {
                setStatus(Component.translatable("skill_atlas.ui.image.limit", Category.MAX_IMAGES), Theme.RED);
                return;
            }
            next.images.add(image.copy());
        } else {
            int index = -1;
            for (int i = 0; i < next.images.size(); i++) {
                if (next.images.get(i).id.equals(image.id)) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                setStatus(Component.translatable("skill_atlas.ui.image.missing"), Theme.RED);
                return;
            }
            next.images.set(index, image.copy());
        }
        JsonArray ops = new JsonArray();
        ops.add(EditOps.categoryOp(next, false));
        saving = true;
        save.enabled = false;
        ClientAtlas.edit(ops, result -> {
            saving = false;
            save.enabled = true;
            if (result.ok()) {
                main.canvas.selectImage(category, image.id);
                close();
            } else {
                setStatus(Names.error(result.key(), result.args()), Theme.RED);
            }
        });
    }
}
