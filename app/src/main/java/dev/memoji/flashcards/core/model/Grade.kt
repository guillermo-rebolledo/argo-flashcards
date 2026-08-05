package dev.memoji.flashcards.core.model

/**
 * The user's verdict on a Card during Review. Two values and no third: there is no "hard", no
 * "easy", and nothing to tune — a Card either came back or it did not, and that is the whole
 * input to the learning model.
 */
enum class Grade {
    KNEW_IT,
    AGAIN,
}
