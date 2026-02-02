package net.questz.mixin;

import java.util.Iterator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.text.LiteralTextContent;
import net.minecraft.text.Text;

@Mixin(AdvancementDisplay.class)
public class AdvancementDisplayMixin {

    @ModifyVariable(method = "fromJson", at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/text/Text$Serializer;fromJson(Lcom/google/gson/JsonElement;)Lnet/minecraft/text/MutableText;", ordinal = 1), ordinal = 1)
    private static Text fromJsonMixin(Text original, JsonObject obj) {
        if (original.getContent() instanceof LiteralTextContent) {

            if (obj.get("description") instanceof JsonObject) {
                JsonObject description = (JsonObject) obj.get("description");
                if (description.has("requirement")) {

                    if (description.get("requirement").isJsonObject()) {
                        JsonObject requirement = (JsonObject) description.get("requirement");
                        Iterator<String> iterator = requirement.keySet().iterator();

                        while (iterator.hasNext()) {
                            String key = iterator.next();
                            if (requirement.get(key).isJsonArray()) {
                                Iterator<JsonElement> requirementField = requirement.get(key).getAsJsonArray().iterator();
                                while (requirementField.hasNext()) {
                                    original = original.copy().append("QK:" + key + "*" + requirementField.next().getAsString());
                                }
                            } else if (requirement.get(key).isJsonPrimitive()) {
                                original = original.copy().append(Text.translatable(requirement.get(key).getAsString()));
                            } else {
                                throw new JsonSyntaxException("Requirement has to have array or string field");
                            }
                        }

                    } else {
                        throw new JsonSyntaxException("Requirement has to be an object");
                    }
                }
            }
        }
        return original;
    }

}
