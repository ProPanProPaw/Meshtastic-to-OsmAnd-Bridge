package com.meshtastic.android.app


class StringHelper {


    companion object {

        fun getFirstCodePoint(text: String?): String? {

            if(text == null || text.isEmpty()) {
                return null
            }

            val symbol = text.let {
                val cp = it.codePointAt(0)
                String(Character.toChars(cp))
            }

            return symbol
        }
    }
}