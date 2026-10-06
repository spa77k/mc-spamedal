package dev.spa.spamedal;

/** メダルの額面。値は1スパメダル何枚ぶんかを表す。 */
enum MedalType {
    ONE(1, "medal_1", "&6"),
    TEN(10, "medal_10", "&f"),
    HUNDRED(100, "medal_100", "&e");

    private final int value;
    private final String modelId;
    private final String color;

    MedalType(int value, String modelId, String color) {
        this.value = value;
        this.modelId = modelId;
        this.color = color;
    }

    int value() {
        return value;
    }

    /** リソースパックのモデル名。spamedal:medal_1 のように使う。 */
    String modelId() {
        return modelId;
    }

    String displayName() {
        return color + value + "スパメダル";
    }

    /** 10枚で1枚になる、ひとつ上の額面。いちばん上なら null。 */
    MedalType larger() {
        return this == ONE ? TEN : this == TEN ? HUNDRED : null;
    }

    /** 1枚を10枚に崩したときの、ひとつ下の額面。いちばん下なら null。 */
    MedalType smaller() {
        return this == HUNDRED ? TEN : this == TEN ? ONE : null;
    }

    static MedalType ofValue(int value) {
        for (MedalType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return null;
    }
}
