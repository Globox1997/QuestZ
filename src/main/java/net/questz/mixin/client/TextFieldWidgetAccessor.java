package net.questz.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(TextFieldWidget.class)
public interface TextFieldWidgetAccessor {

    @Accessor("maxLength")
    int getMaxLength();

    @Accessor("selectionStart")
    int getSelectionStart();

    @Accessor("selectionEnd")
    int getSelectionEnd();

}
