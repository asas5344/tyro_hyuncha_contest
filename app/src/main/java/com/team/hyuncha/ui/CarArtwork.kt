package com.team.hyuncha.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.team.hyuncha.R
import com.team.hyuncha.data.CarPresets
import com.team.hyuncha.data.CarSpec

@Composable
internal fun CarArtwork(car: CarSpec, modifier: Modifier = Modifier) {
    val resource = when (car.name) {
        CarPresets.AVANTE.name -> R.drawable.car_avante
        CarPresets.TUCSON.name -> R.drawable.car_tucson
        CarPresets.SANTA_FE.name -> R.drawable.car_santafe
        else -> R.drawable.car_grandeur
    }
    Image(painter = painterResource(resource), contentDescription = "${car.name} 차량 일러스트",
        modifier = modifier, contentScale = ContentScale.Fit)
}
