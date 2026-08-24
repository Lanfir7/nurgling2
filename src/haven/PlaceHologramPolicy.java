package haven;

/**
 * When to retry attaching a placement hologram vs swallow the error.
 *
 * {@link Loading} on a live visual session must propagate so {@link Loader}
 * retries and the ghost appears. Headless / demoted sessions swallow it:
 * the GL ghost never finishes, and stockpile bots wait only for the server
 * {@code place} message ({@code WaitPlob(false)}).
 */
public final class PlaceHologramPolicy {
    private PlaceHologramPolicy() {}

    public static boolean retryPlaceOn(Throwable t, boolean visualSession) {
	return visualSession && (t instanceof Loading);
    }

    public static boolean isVisualPlaceSession(boolean hasUi, boolean hasEnv, boolean hasBasic,
					      boolean cliHeadless, boolean envHeadless, boolean sessionHeadless) {
	return hasUi && hasEnv && hasBasic && !cliHeadless && !envHeadless && !sessionHeadless;
    }
}
