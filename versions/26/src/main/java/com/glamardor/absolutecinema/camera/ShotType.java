package com.glamardor.absolutecinema.camera;

/** The grammar of the directed camera: one entry per kind of move it knows how to make. */
public enum ShotType {
	/** Slow arc around the subject at a steady distance. */
	ORBIT,
	/** Push in towards the subject. */
	DOLLY_IN,
	/** Pull away from the subject. */
	DOLLY_OUT,
	/** Descends from above down to eye level. */
	CRANE_DOWN,
	/** Rises from low up over the subject. */
	CRANE_UP,
	/** Tight framing on the face, barely moving. */
	CLOSE_UP,
	/** Wide establishing frame that takes in the room. */
	WIDE,
	/** Looks past the shoulder of the subject towards whoever they face. */
	OVER_SHOULDER,
	/** Locked-off frame with only a hand-held drift on it. */
	STATIC_DRIFT,
	/** Slides sideways past the subject, keeping them centred. */
	TRACKING
}
