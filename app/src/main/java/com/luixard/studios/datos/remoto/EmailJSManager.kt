package com.luixard.studios.datos.remoto

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EmailJSManager {
    private var codigoGenerado: String = ""

    // Genera el código de 5 dígitos
    fun generarCodigoVerificacion(): String {
        codigoGenerado = (10000..99999).random().toString()
        return codigoGenerado
    }

    // Prepara el JSON para la API
    fun prepararDatosEmail(nombre: String, correo: String): Map<String, Any> {
        return mapOf(
            "service_id" to "service_90aab1h",
            "template_id" to "template_rbivst8",
            "user_id" to "akRDSWMA2agOzDoiW",
            "template_params" to mapOf(
                "nombre_usuario" to nombre,
                "codigo" to codigoGenerado,
                "to_email" to correo
            )
        )
    }

    // Realiza la petición de red
    suspend fun enviarEmail(datos: Map<String, Any>): Boolean = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val body = JSONObject(datos).toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.emailjs.com/api/v1.0/email/send")
            .post(body)
            .build()

        return@withContext try {
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getCodigoActual() = codigoGenerado
}