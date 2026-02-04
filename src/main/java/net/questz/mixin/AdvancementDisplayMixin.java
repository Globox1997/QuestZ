package net.questz.mixin;

import com.mojang.datafixers.kinds.App;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementFrame;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;
import java.util.function.Function;


@Mixin(AdvancementDisplay.class)
public class AdvancementDisplayMixin {

    @Redirect(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/codecs/RecordCodecBuilder;create(Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"))
    private static Codec<AdvancementDisplay> redirectCodec(Function<RecordCodecBuilder.Instance<AdvancementDisplay>, ? extends App<RecordCodecBuilder.Mu<AdvancementDisplay>, AdvancementDisplay>> builder) {
        return RecordCodecBuilder.create(instance -> instance.group(
                ItemStack.VALIDATED_CODEC.fieldOf("icon").forGetter(AdvancementDisplay::getIcon),
                TextCodecs.CODEC.fieldOf("title").forGetter(AdvancementDisplay::getTitle),
                TextCodecs.CODEC.fieldOf("description").forGetter(AdvancementDisplay::getDescription),
                Codec.PASSTHROUGH.optionalFieldOf("requirement").forGetter(d -> Optional.empty()),
                Identifier.CODEC.optionalFieldOf("background").forGetter(AdvancementDisplay::getBackground),
                AdvancementFrame.CODEC.optionalFieldOf("frame", AdvancementFrame.TASK).forGetter(AdvancementDisplay::getFrame),
                Codec.BOOL.optionalFieldOf("show_toast", true).forGetter(AdvancementDisplay::shouldShowToast),
                Codec.BOOL.optionalFieldOf("announce_to_chat", true).forGetter(AdvancementDisplay::shouldAnnounceToChat),
                Codec.BOOL.optionalFieldOf("hidden", false).forGetter(AdvancementDisplay::isHidden)
        ).apply(instance, (icon, title, description, requirement, background, frame, showToast, announceToChat, hidden) -> {

            Text finalDescription = description;
            if (requirement.isPresent()) {
                finalDescription = addRequirementText(description, requirement.get());
            }

            return new AdvancementDisplay(icon, title, finalDescription, background, frame, showToast, announceToChat, hidden);
        }));
    }

    @Unique
    private static Text addRequirementText(Text original, Dynamic<?> requirement) {
        MutableText modified = original.copy();

        requirement.asMap(Dynamic::asString, Function.identity()).forEach((keyResult, value) -> {
            String key = keyResult.getOrThrow();

            if (value.asStreamOpt().isSuccess()) {
                value.asStream().forEach(el -> {
                    modified.append("QK:" + key + "*" + el.asString(""));
                });
            } else {
                modified.append(Text.translatable(value.asString("")));
            }
        });

        return modified;
    }

}
