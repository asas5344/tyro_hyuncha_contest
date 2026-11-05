package com.team.hyuncha.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import com.team.hyuncha.R
import com.team.hyuncha.data.CarPresets
import com.team.hyuncha.data.CarSpec

/**
 * 위에서 본 차 이미지 (앞이 오른쪽, 투명 배경, 범퍼 끝까지 잘라둔 것).
 * 사이드미러가 차체 밖으로 튀어나와 있어서, 이미지 높이 중 차체가 차지하는 위·아래 비율을 같이 들고 있음.
 * 그릴 때 차체 부분이 실제 전폭에, 이미지 가로가 실제 전장에 맞도록 늘림.
 */
class CarTopSprite(val image: ImageBitmap, val bodyTop: Float, val bodyBottom: Float)

/** 차종 → 탑뷰 이미지. bodyTop/bodyBottom은 배경을 지우면서 이미지에서 잰 값 */
@Composable
fun rememberCarTopSprites(): Map<String, CarTopSprite> {
    val avante = ImageBitmap.imageResource(R.drawable.car_top_avante)
    val grandeur = ImageBitmap.imageResource(R.drawable.car_top_grandeur)
    val tucson = ImageBitmap.imageResource(R.drawable.car_top_tucson)
    val santaFe = ImageBitmap.imageResource(R.drawable.car_top_santafe)
    return mapOf(
        CarPresets.AVANTE.name to CarTopSprite(avante, 0.0671f, 0.9514f),
        CarPresets.GRANDEUR.name to CarTopSprite(grandeur, 0.0683f, 0.9706f),
        CarPresets.TUCSON.name to CarTopSprite(tucson, 0.0540f, 0.9713f),
        CarPresets.SANTA_FE.name to CarTopSprite(santaFe, 0.0651f, 0.9683f),
    )
}

fun Map<String, CarTopSprite>.forCar(car: CarSpec): CarTopSprite? = this[car.name]
