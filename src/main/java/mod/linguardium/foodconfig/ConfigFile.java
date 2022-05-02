package mod.linguardium.foodconfig;

import java.util.HashMap;

public class ConfigFile {
    HashMap<String,FoodStats> foodMap = new HashMap<>();
    public static class FoodStats {
        public float saturation = -1f;
        public int hunger = -1;
        FoodStats() {}
        FoodStats(int hunger, float saturation) {this.hunger=hunger;this.saturation=saturation;}
    }
}
