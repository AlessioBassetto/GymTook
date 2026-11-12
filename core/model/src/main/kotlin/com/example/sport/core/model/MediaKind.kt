package com.example.sport.core.model

/** Tipo di contenuto multimediale di un esercizio. Nomi persistiti come TEXT: non rinominare senza Migration. */
enum class MediaKind {
    /**
     * Fotogramma statico di una sequenza (es. posizione iniziale e finale). Due o più fotogrammi
     * vengono animati in dissolvenza incrociata come "mini-loop" quando non esiste un video.
     */
    IMAGE_FRAME,

    /** Video breve in loop, senza audio. */
    VIDEO_LOOP,
}
