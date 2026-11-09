package com.team.hyuncha.render3d

import org.junit.Assert.*
import org.junit.Test

class RenderSizeTest {
    @Test fun initialTextureCallbackUsesBufferInsteadOfLayoutDimensions() {
        val size=RenderSize.surface(1242,1090,820,720)
        assertEquals(RenderSize(820,720),size)
        assertEquals(820.0/720,size.aspect,0.0)
    }
    @Test fun subsequentBufferCallbackKeepsTheSameViewport() {
        assertEquals(RenderSize.surface(1242,1090,820,720),RenderSize.surface(820,720,820,720))
    }
    @Test fun fullResolutionAndZeroSizeCallbacksAreSafe() {
        assertEquals(RenderSize(1920,1080),RenderSize.surface(1920,1080,0,0))
        assertEquals(RenderSize(1,1),RenderSize.surface(0,0,0,0))
    }
}
