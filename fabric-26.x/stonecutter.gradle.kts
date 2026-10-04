plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1" /* [SC] DO NOT EDIT */

stonecutter tasks {
    order("publishModrinth")
}
