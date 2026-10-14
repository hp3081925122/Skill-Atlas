package com.hp.skillatlas;

import net.minecraftforge.common.ForgeConfigSpec;

public final class AtlasConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec.IntValue EDIT_PERMISSION;
    public static final ForgeConfigSpec.BooleanValue COUNT_CREATIVE;
    public static final ForgeConfigSpec.BooleanValue NOTIFY_POINTS;

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.IntValue VIRTUAL_HEIGHT;
    public static final ForgeConfigSpec.DoubleValue PANEL_WIDTH;
    public static final ForgeConfigSpec.DoubleValue PANEL_HEIGHT;
    public static final ForgeConfigSpec.BooleanValue SHOW_NODE_NAMES;

    static {
        ForgeConfigSpec.Builder server = new ForgeConfigSpec.Builder();
        EDIT_PERMISSION = server
                .comment("编辑技能树所需的权限等级 / Permission level required to edit the skill atlas")
                .defineInRange("editPermissionLevel", 2, 0, 4);
        COUNT_CREATIVE = server
                .comment("创造与旁观模式下是否累计技能点获取进度 / Whether creative and spectator players make progress toward earning points")
                .define("countCreativePlayers", false);
        NOTIFY_POINTS = server
                .comment("获得技能点时是否在动作栏提示 / Show an action bar message when skill points are gained")
                .define("notifyPointGain", true);
        SERVER_SPEC = server.build();

        ForgeConfigSpec.Builder client = new ForgeConfigSpec.Builder();
        VIRTUAL_HEIGHT = client
                .comment("界面虚拟高度，数值越大界面越小，与原版界面尺寸设置无关 / Virtual UI height; larger values make the atlas smaller, independent of the vanilla GUI scale")
                .defineInRange("virtualHeight", 360, 200, 1200);
        PANEL_WIDTH = client
                .comment("技能树窗口占屏幕宽度的比例 / Fraction of the screen width used by the atlas window")
                .defineInRange("panelWidth", 0.86D, 0.4D, 1.0D);
        PANEL_HEIGHT = client
                .comment("技能树窗口占屏幕高度的比例 / Fraction of the screen height used by the atlas window")
                .defineInRange("panelHeight", 0.88D, 0.4D, 1.0D);
        SHOW_NODE_NAMES = client
                .comment("在节点下方显示技能名称 / Show skill names below nodes")
                .define("showNodeNames", true);
        CLIENT_SPEC = client.build();
    }

    private AtlasConfig() {
    }
}
