package net.questz.task;

import com.mojang.serialization.MapCodec;

public record TaskType<T extends Task>(MapCodec<T> codec) {
}
