package io.github.notstirred.dasm.mod;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public record DasmConfig(
        @SerializedName("requiredVersion") String requiredVersion,
        @SerializedName("dasm") List<String> dasmClasses
) {
}
