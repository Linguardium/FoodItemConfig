package mod.linguardium.foodconfig;

import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.FoodComponent;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ConfigFile {
    HashMap<Identifier,FoodStats> foodMap = new HashMap<>();
    public static class FoodStats {
        public Float saturation;
        public Integer hunger;
        public Boolean meat;
        public Boolean alwaysEdible;
        public Boolean snack;
        public List<EffectWithChance> effects=new ArrayList<>();
        FoodStats() {}
        FoodStats(int hunger, float saturation) {this.hunger=hunger;this.saturation=saturation;}
        public static FoodStats fromFoodComponent(FoodComponent component) {
            FoodStats stat = new FoodStats();
            if (component != null) {
                stat.hunger= component.getHunger();
                stat.saturation=component.getSaturationModifier();
                stat.meat=component.isMeat();
                stat.snack=component.isSnack();
                stat.alwaysEdible=component.isAlwaysEdible();
                for (Pair<StatusEffectInstance,Float> statusEffect:component.getStatusEffects()) {
                    stat.effects.add(new EffectWithChance(statusEffect.getFirst(),statusEffect.getSecond()));
                }
            }
            return stat;
        }
        public FoodComponent toFoodComponent() {
            FoodComponent.Builder builder = new FoodComponent.Builder();
            if (hunger > 0) builder.hunger(hunger);
            if (saturation>0) builder.saturationModifier(saturation);
            if (snack) builder.snack();
            if (meat) builder.meat();
            if (alwaysEdible) builder.alwaysEdible();
            for (EffectWithChance effect: effects){
                builder.statusEffect(effect.effect.toStatusEffectInstance(),effect.chancePercent);
            }
            return builder.build();
        }
    }
    public  static class EffectWithChance {
        StatusEffectInstanceConfig effect;
        float chancePercent;
        EffectWithChance(StatusEffectInstance instance,Float chance) {
            if (instance != null) {
                effect=StatusEffectInstanceConfig.fromStatusEffectInstance(instance);
            }
            chancePercent=(chance!=null)?chance:1.0f;
        }
    }
    public static class StatusEffectInstanceConfig {
        Identifier type=new Identifier("minecraft:regeneration");
        int duration=200;
        int amplifier=1;
        boolean ambient=false;
        boolean permanent=false;
        boolean showParticles = true;
        boolean showIcon = true;
        StatusEffectInstance toStatusEffectInstance() {
            if (type!=null && Registry.STATUS_EFFECT.containsId(type)) {
                return new StatusEffectInstance(Registry.STATUS_EFFECT.get(type),duration,amplifier,ambient,showParticles,showIcon);
            }
            FoodConfig.LOGGER.error("Invalid status effect id {}",type.toString());
            return null;
        }
        public static StatusEffectInstanceConfig fromStatusEffectInstance(StatusEffectInstance instance) {
            StatusEffectInstanceConfig cfg= new StatusEffectInstanceConfig();
            cfg.type = Registry.STATUS_EFFECT.getId(instance.getEffectType());
            cfg.duration=instance.getDuration();
            cfg.amplifier=instance.getAmplifier();
            cfg.ambient= instance.isAmbient();
            cfg.permanent=instance.isPermanent();
            cfg.showIcon=instance.shouldShowIcon();
            cfg.showParticles= instance.shouldShowParticles();
            return cfg;
        }
    }
}
