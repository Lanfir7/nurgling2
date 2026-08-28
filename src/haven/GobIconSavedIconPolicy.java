package haven;

/**
 * When reloading a saved {@link GobIcon} from disk, {@link Loading} is not a
 * real failure: code deps such as {@code gfx/terobjs/mm/kritter} may still be
 * in the resource queue. Rethrow so {@link Loader} waits and retries instead
 * of dropping the icon for the whole session.
 */
public final class GobIconSavedIconPolicy {
    private GobIconSavedIconPolicy() {}

    public static boolean rethrow(Throwable t, boolean cached) {
	if(t instanceof Loading)
	    return true;
	if((t instanceof Resource.BadVersionException) && !cached)
	    return true;
	if((t instanceof LinkageError) && !cached)
	    return true;
	return false;
    }
}
