package mod.linguardium.foodconfig.mixin;

import mod.linguardium.foodconfig.FoodConfig;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.item.FoodComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodComponent.class)
public interface FoodComponentAccessor {
    @Mutable
    @Accessor("saturationModifier")
    void setSaturationValue(float saturation);
    @Mutable
    @Accessor("hunger")
    void setHungerValue(int hunger);

}
