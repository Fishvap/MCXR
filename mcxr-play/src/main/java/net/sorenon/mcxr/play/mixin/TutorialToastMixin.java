package net.sorenon.mcxr.play.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// TutorialTost Mixin:
// Simply removes the tutorial toasts. They are very annoying in QuestCraft and are persistat.
@Mixin(TutorialToast.class)
public class TutorialToastMixin {

    @Shadow private Toast.Visibility visibility;

    @Inject(at=@At("TAIL"), method = "render", cancellable = true)
    public void renderMixin(PoseStack poseStack, ToastComponent toastComponent, long l, CallbackInfoReturnable<Toast.Visibility> cir) {
        cir.cancel();
        this.visibility = Toast.Visibility.HIDE;
    }

}
