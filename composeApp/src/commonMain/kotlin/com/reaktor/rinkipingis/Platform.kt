package com.reaktor.rinkipingis

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform