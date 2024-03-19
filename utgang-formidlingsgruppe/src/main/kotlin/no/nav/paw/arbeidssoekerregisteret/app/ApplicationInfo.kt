package no.nav.paw.arbeidssoekerregisteret.app

object ApplicationInfo {
    val id = System.getenv("IMAGE_WITH_VERSION")
        ?: "paw-arbeidssoekerregisteret-utgang-formidlingsgruppe:LOCAL"
}