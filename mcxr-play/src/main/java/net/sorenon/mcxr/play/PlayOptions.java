package net.sorenon.mcxr.play;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import org.lwjgl.glfw.GLFW;

public class PlayOptions {

    private static FileConfig fileConfig;

    public static boolean xrUninitialized = false;
    public static boolean xrPaused = false;

    public static MoveDirectionPose walkDirection = MoveDirectionPose.LeftHand;
    public static MoveDirectionPose swimDirection = MoveDirectionPose.RightHand;
    public static MoveDirectionPose flyDirection = MoveDirectionPose.RightHand;

    public static boolean smoothTurning = false;
    public static float snapTurnAmount = 22f;
    public static float smoothTurnRate = 120f;

    /**
     * The angle to rotate the player's in-game hand for a more comfortable experience
     * May be different for different controllers -> needs testing
     */
    public static float handPitchAdjust = 30;

    public static float SSAA = 1;

    public static IndexTouchpad indexTouchpadState = IndexTouchpad.Off;

    public static void init() {
        fileConfig = FileConfig.of(FabricLoader.getInstance().getConfigDir().resolve("mcxr-play.toml"));
    }

    public static void save() {
        fileConfig.set("disableMCXR", xrUninitialized);
        fileConfig.set("pauseMCXR", xrPaused);

        fileConfig.set("walkDirection", walkDirection);
        fileConfig.set("swimDirection", swimDirection);
        fileConfig.set("flyDirection", flyDirection);

        fileConfig.set("handPitchAdjust", handPitchAdjust);

        fileConfig.set("smoothTurning", smoothTurning);
        fileConfig.set("snapTurnAmount", snapTurnAmount);
        fileConfig.set("smoothTurnRate", smoothTurnRate);
        fileConfig.set("indexTouchpadState", indexTouchpadState);

        fileConfig.set("SSAA", SSAA);
        fileConfig.save();
    }

    public static void load() {
        fileConfig.load();
        xrUninitialized = fileConfig.getOrElse("disableMCXR", false);
        xrPaused = fileConfig.getOrElse("pauseMCXR", false);

        walkDirection = fileConfig.getEnumOrElse("walkDirection", MoveDirectionPose.LeftHand);
        swimDirection = fileConfig.getEnumOrElse("swimDirection", MoveDirectionPose.RightHand);
        flyDirection = fileConfig.getEnumOrElse("flyDirection", MoveDirectionPose.RightHand);

        handPitchAdjust = fileConfig.<Number>getOrElse("handPitchAdjust", 30f).floatValue();

        smoothTurning = fileConfig.getOrElse("smoothTurning", false);
        snapTurnAmount = fileConfig.<Number>getOrElse("snapTurnAmount", 22f).floatValue();
        smoothTurnRate = fileConfig.<Number>getOrElse("smoothTurnRate", 120f).floatValue();
        indexTouchpadState = fileConfig.getEnumOrElse("indexTouchpadState", IndexTouchpad.Off);

        SSAA = fileConfig.<Number>getOrElse("SSAA", 1).floatValue();
    }

    public enum IndexTouchpad {
        Off,
        RightForward,
        LeftForward;


        public Component toComponent() {
            switch (this) {
                case Off -> {
                    return new TranslatableComponent("mcxr.index_touchpad.off");
                }
                case RightForward -> {
                    return new TranslatableComponent("mcxr.index_touchpad.right_hand");
                }
                case LeftForward -> {
                    return new TranslatableComponent("mcxr.index_touchpad.left_hand");
                }
                default -> throw new IllegalStateException("Unexpected value: " + this);
            }
        }

        public IndexTouchpad iterate() {
            boolean next = !InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT);
            switch (this) {
                case Off -> {
                    return next ? RightForward : LeftForward;
                }
                case RightForward -> {
                    return next ? LeftForward : Off;
                }
                case LeftForward -> {
                    return next ? Off : RightForward;
                }
                default -> throw new IllegalStateException("Unexpected value: " + this);
            }
        }
    }
}
