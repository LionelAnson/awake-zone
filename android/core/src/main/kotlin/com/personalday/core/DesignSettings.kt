package com.personalday.core

/** Shared row styling for both columns. Values are percentages of the approved layout. */
data class DesignSettings(
    val timeScale: Int = 100,
    val middleScale: Int = 100,
    val bottomScale: Int = 100,
    val timeOpacity: Int = 100,
    val middleOpacity: Int = 50,
    val bottomOpacity: Int = 50,
    val progressThickness: Int = 100,
) {
    fun validate() {
        require(timeScale in 60..110 && middleScale in 60..110 && bottomScale in 60..110) {
            "字号应为当前设计的 60—110%。"
        }
        require(timeOpacity in 0..100 && middleOpacity in 0..100 && bottomOpacity in 0..100) {
            "文字不透明度应为 0—100%。"
        }
        require(progressThickness in 50..200) { "进度条粗细应为当前设计的 50—200%。" }
    }
}
