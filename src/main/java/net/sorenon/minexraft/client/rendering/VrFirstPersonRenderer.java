package net.sorenon.minexraft.client.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.*;
import net.minecraft.world.LightType;
import net.sorenon.minexraft.client.FlatGuiManager;
import net.sorenon.minexraft.client.MineXRaftClient;
import net.sorenon.minexraft.client.Pose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.BiFunction;

import static net.minecraft.client.render.RenderPhase.*;

/**
 * Work in progress
 */
public class VrFirstPersonRenderer {

    private final FlatGuiManager FGM;

    private final ModelPart[] slimArmModel = new ModelPart[2];
    private final ModelPart[] armModel = new ModelPart[2];

    public VrFirstPersonRenderer(FlatGuiManager flatGuiManager) {
        this.FGM = flatGuiManager;
        for (int slim = 0; slim < 2; slim++) {
            ModelPart[] arr = slim == 0 ? armModel : slimArmModel;
            for (int hand = 0; hand < 2; hand++) {
                ModelPartBuilder builder = ModelPartBuilder.create();

                if (hand == 0) {
                    builder.uv(32, 48);
                } else {
                    builder.uv(40, 16);
                }

                if (slim == 0) {
                    builder.cuboid(0, 0, 0, 4, 12, 4, hand == 0);
                } else {
                    builder.cuboid(0.5f, 0, 0, 3, 12, 4, hand == 0);
                }

                arr[hand] = new ModelData().getRoot().addChild(
                        "arm",
                        builder,
                        ModelTransform.NONE).createPart(64, 64);
            }
        }
    }

    public void renderAfterEntities(WorldRenderContext context) {
        Entity camEntity = context.camera().getFocusedEntity();

        if (camEntity != null) {
            renderShadow(context, camEntity);

            if (camEntity instanceof LivingEntity entity) {
                for (int hand = 0; hand < 2; hand++) {
                    if (!MineXRaftClient.vanillaCompatActionSet.isHandActive[hand]) {
                        continue;
                    }

                    ItemStack stack = hand == 0 ? entity.getOffHandStack() : entity.getMainHandStack();

                    if (!stack.isEmpty()) {
                        MatrixStack matrices = context.matrixStack();
                        boolean mainHand = hand == MineXRaftClient.mainHand;
                        matrices.push();
                        transformToHand(matrices, hand);

                        int light = LightmapTextureManager.pack(entity.world.getLightLevel(LightType.BLOCK, entity.getBlockPos()), entity.world.getLightLevel(LightType.SKY, entity.getBlockPos()));

                        if (mainHand) {
                            float swing = -0.4f * MathHelper.sin((float) (Math.sqrt(entity.getHandSwingProgress(context.tickDelta())) * Math.PI * 2));
                            matrices.multiply(Vec3f.POSITIVE_X.getRadialQuaternion(swing));
                        }

                        MinecraftClient.getInstance().getHeldItemRenderer().renderItem(
                                entity,
                                stack,
                                hand == 0 ? ModelTransformation.Mode.THIRD_PERSON_LEFT_HAND : ModelTransformation.Mode.THIRD_PERSON_RIGHT_HAND,
                                hand == 0,
                                matrices,
                                context.consumers(),
                                light
                        );

                        matrices.pop();
                    }
                }
            }
        }
        hands(context);
    }

    public void hands(WorldRenderContext context) {
        MatrixStack matrices = context.matrixStack();

        for (int hand = 0; hand < 2; hand++) {
            if (!MineXRaftClient.vanillaCompatActionSet.isHandActive[hand]) {
                continue;
            }
            matrices.push();

            transformToHand(matrices, hand);

            matrices.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(-90.0F));
            matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(180.0F));

            matrices.translate(-2 / 16f, -12 / 16f, 0);

            if (hand == 0 && FGM.pos == null) {//TODO find a good way to render the hud
                matrices.push();
                matrices.translate(2 / 16f, 9 / 16f, -1 / 16f);
                matrices.translate(-1 / 16f, 1 / 16f, 0);
                float handGuiScale = 1f / 2000;
                matrices.scale(handGuiScale, handGuiScale, handGuiScale);
//                matrices.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(-75.0F));
                matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(25f));
                renderGuiQuad(matrices.peek(), context.consumers());
                matrices.pop();
            }

            //Draw hand
            if (context.camera().getFocusedEntity() instanceof ClientPlayerEntity player) {
                matrices.push();
                ModelPart armModel;
                if (player.getModel().equals("slim")) {
                    armModel = this.slimArmModel[hand];
                } else {
                    armModel = this.armModel[hand];
                }

                int light = LightmapTextureManager.pack(player.world.getLightLevel(LightType.BLOCK, player.getBlockPos()), player.world.getLightLevel(LightType.SKY, player.getBlockPos()));

                VertexConsumer consumer = context.consumers().getBuffer(RenderLayer.getEntityTranslucent(player.getSkinTexture()));
                armModel.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV);
                matrices.pop();
            }

            matrices.pop();
        }

        if (FGM.pos != null) {
            matrices.push();
            Vec3d pos = FGM.pos.subtract(context.camera().getPos());
            matrices.translate(pos.x, pos.y, pos.z);
//            float s = 1 / 2000f;
//            matrices.scale(s, s, s);
            renderGuiQuad(matrices.peek(), context.consumers());
            matrices.pop();
        }
    }

    public void transformToHand(MatrixStack matrices, int hand) {
        Pose pose = MineXRaftClient.vanillaCompatActionSet.poses[hand];
        Vec3d gripPos = pose.getPosMc();
        Vector3f eyePos = MineXRaftClient.eyePose.getPos();

        //Transform to controller
        matrices.translate(gripPos.x - eyePos.x(), gripPos.y - eyePos.y(), gripPos.z - eyePos.z());
        matrices.multiply(pose.getOrientationMc());

        //Apply adjustments
        matrices.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(-90.0F));
        matrices.scale(0.4f, 0.4f, 0.4f);
        matrices.translate(0, 1 / 16f, -1.5f / 16f);
        matrices.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(MineXRaftClient.handPitchAdjust));
    }

    public void renderShadow(WorldRenderContext context, Entity camEntity) {
        MatrixStack matrices = context.matrixStack();
        Vec3d camPos = context.camera().getPos();
        matrices.push();
        double x = MathHelper.lerp(context.tickDelta(), camEntity.lastRenderX, camEntity.getX());
        double y = MathHelper.lerp(context.tickDelta(), camEntity.lastRenderY, camEntity.getY());
        double z = MathHelper.lerp(context.tickDelta(), camEntity.lastRenderZ, camEntity.getZ());
        matrices.translate(x - camPos.x, y - camPos.y, z - camPos.z);
        MatrixStack.Entry entry = matrices.peek();

        RenderLayer SHADOW_LAYER = RenderLayer.getEntityShadow(new Identifier("textures/misc/shadow.png"));
        VertexConsumer vertexConsumer = context.consumers().getBuffer(SHADOW_LAYER);

        float alpha = MathHelper.clamp((float) Math.sqrt(camPos.squaredDistanceTo(x, y, z)) / 2f - 0.5f, 0.25f, 1);
        float radius = camEntity.getWidth() / 2;
        float y0 = 0.005f;

        vertexConsumer.vertex(entry.getModel(), -radius, y0, -radius).color(1.0F, 1.0F, 1.0F, alpha).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(entry.getNormal(), 0.0F, 1.0F, 0.0F).next();
        vertexConsumer.vertex(entry.getModel(), -radius, y0, radius).color(1.0F, 1.0F, 1.0F, alpha).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(entry.getNormal(), 0.0F, 1.0F, 0.0F).next();
        vertexConsumer.vertex(entry.getModel(), radius, y0, radius).color(1.0F, 1.0F, 1.0F, alpha).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(entry.getNormal(), 0.0F, 1.0F, 0.0F).next();
        vertexConsumer.vertex(entry.getModel(), radius, y0, -radius).color(1.0F, 1.0F, 1.0F, alpha).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(entry.getNormal(), 0.0F, 1.0F, 0.0F).next();

        matrices.pop();
    }

    public void renderHandsGui() {
        MatrixStack matrices = RenderSystem.getModelViewStack();
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();

        for (int hand = 0; hand < 2; hand++) {
            if (!MineXRaftClient.vanillaCompatActionSet.isHandActive[hand]) {
                continue;
            }

            RenderSystem.setShader(GameRenderer::getRenderTypeLinesShader);
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableTexture();
            RenderSystem.disableDepthTest();

            matrices.push();
            Pose pose = MineXRaftClient.vanillaCompatActionSet.poses[hand];
            Vec3d gripPos = pose.getPosMc();
            Vector3f eyePos = MineXRaftClient.eyePose.getPos();
            matrices.translate(gripPos.x - eyePos.x(), gripPos.y - eyePos.y(), gripPos.z - eyePos.z());

            matrices.push();
            Quaternionf quat = pose.getOrientation().rotateX((float) Math.toRadians(MineXRaftClient.handPitchAdjust), new Quaternionf());
            matrices.multiply(new Quaternion(quat.x, quat.y, quat.z, quat.w));


            if (hand == MineXRaftClient.mainHand) {
                RenderSystem.disableTexture();
                RenderSystem.depthMask(false);
                RenderSystem.applyModelViewMatrix();

                RenderSystem.lineWidth(4.0F);
                buffer.begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
                buffer.vertex(0, 0, 0).color(0f, 0f, 0f, 1f).normal(0, -1, 0).next();
                buffer.vertex(0, -10, 0).color(0.1f, 0.1f, 0.1f, 1f).normal(0, -1, 0).next();
                Tessellator.getInstance().draw();

                RenderSystem.enableDepthTest();
                RenderSystem.lineWidth(2.0F);
                buffer.begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
                buffer.vertex(0, 0, 0).color(1f, 0.1f, 0.1f, 1f).normal(0, -1, 0).next();
                buffer.vertex(0, -10, 0).color(1f, 0.1f, 0.1f, 1f).normal(0, -1, 0).next();
                Tessellator.getInstance().draw();
                RenderSystem.disableDepthTest();
            }

            if (MinecraftClient.getInstance().options.debugEnabled) {
                matrices.scale(0.05f, 0.05f, 0.05f);
                RenderSystem.applyModelViewMatrix();
                RenderSystem.renderCrosshair(1);

                matrices.pop();
                matrices.multiply(pose.getOrientationMc());
                matrices.scale(0.1f, 0.1f, 0.1f);
                RenderSystem.applyModelViewMatrix();

                RenderSystem.renderCrosshair(1);
            } else {
                matrices.pop();
            }

            matrices.pop();
        }

        matrices.push();
        matrices.loadIdentity();
        RenderSystem.applyModelViewMatrix();
        matrices.pop();

        for (int hand = 0; hand < 2; hand++) {
            if (!MineXRaftClient.vanillaCompatActionSet.isHandActive[hand]) {
                continue;
            }
            matrices.push();

            transformToHand(matrices, hand);

            matrices.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(-90.0F));
            matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(180.0F));

            matrices.translate(-2 / 16f, -12 / 16f, 0);

            if (hand == 0) {
                matrices.push();
                matrices.translate(2 / 16f, 9 / 16f, -1 / 16f);
                matrices.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(-75f));
                RenderSystem.setShader(GameRenderer::getRenderTypeEntityTranslucentShader);
                renderGuiQuad(matrices.peek(), MinecraftClient.getInstance().getBufferBuilders().getEffectVertexConsumers());
                MinecraftClient.getInstance().getBufferBuilders().getEffectVertexConsumers().draw();
                matrices.pop();
            }
            matrices.pop();
        }

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableTexture();
    }

    private void renderGuiQuad(MatrixStack.Entry transform, VertexConsumerProvider consumers) {
        Framebuffer guiFramebuffer = FGM.framebuffer;

        float size = 1f;
        float x = size / 2;
        float y = size * guiFramebuffer.textureHeight / guiFramebuffer.textureWidth;
//        float x = guiFramebuffer.textureWidth / 2f;
//        float y = guiFramebuffer.textureHeight;

        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getWaterMask());
        Matrix4f modelMatrix = transform.getModel();
        Matrix3f normalMatrix = transform.getNormal();
//        consumer.vertex(modelMatrix, -x, y, 0).color(255, 255, 255, 255).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(normalMatrix, 0, 0, -1).next();
//        consumer.vertex(modelMatrix, x, y, 0).color(255, 255, 255, 255).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(normalMatrix, 0, 0, -1).next();
//        consumer.vertex(modelMatrix, x, 0, 0).color(255, 255, 255, 255).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(normalMatrix, 0, 0, -1).next();
//        consumer.vertex(modelMatrix, -x, 0, 0).color(255, 255, 255, 255).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(normalMatrix, 0, 0, -1).next();
        consumer = consumers.getBuffer(ENTITY_TRANSLUCENT_ALWAYS.apply(MineXRaftClient.INSTANCE.flatGuiManager.texture, true));
//        consumer = consumers.getBuffer(RenderLayer.getEntityTranslucent(MineXRaftClient.INSTANCE.flatGuiManager.texture));
        consumer.vertex(modelMatrix, -x, y, 0).color(255, 255, 255, 255).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(normalMatrix, 0, 0, -1).next();
        consumer.vertex(modelMatrix, x, y, 0).color(255, 255, 255, 255).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(normalMatrix, 0, 0, -1).next();
        consumer.vertex(modelMatrix, x, 0, 0).color(255, 255, 255, 255).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(normalMatrix, 0, 0, -1).next();
        consumer.vertex(modelMatrix, -x, 0, 0).color(255, 255, 255, 255).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(normalMatrix, 0, 0, -1).next();

    }

    private static final BiFunction<Identifier, Boolean, RenderLayer> ENTITY_TRANSLUCENT_ALWAYS = Util.memoize((texture, affectsOutline) -> {
        RenderLayer.MultiPhaseParameters multiPhaseParameters = RenderLayer.MultiPhaseParameters.builder().shader(RenderPhase.ENTITY_TRANSLUCENT_SHADER).texture(new RenderPhase.Texture(texture, false, false)).transparency(TRANSLUCENT_TRANSPARENCY).cull(DISABLE_CULLING).lightmap(ENABLE_LIGHTMAP).overlay(ENABLE_OVERLAY_COLOR).depthTest(ALWAYS_DEPTH_TEST).build(affectsOutline);
        return RenderLayer.of("entity_translucent", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS, 256, true, true, multiPhaseParameters);
    });
}
