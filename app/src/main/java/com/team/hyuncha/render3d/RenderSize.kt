package com.team.hyuncha.render3d

/** TextureView's first resize callback reports layout pixels, even when its buffer is smaller. */
internal data class RenderSize(val width: Int,val height: Int) {
    val aspect get()=width.toDouble()/height
    companion object {
        fun surface(width: Int,height: Int,requestedWidth: Int,requestedHeight: Int): RenderSize =
            if(requestedWidth>0 && requestedHeight>0) RenderSize(requestedWidth,requestedHeight)
            else RenderSize(width.coerceAtLeast(1),height.coerceAtLeast(1))
    }
}
