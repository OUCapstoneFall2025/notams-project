package ou.capstone.notams.prioritize;

import ou.capstone.notams.Notam;
import java.util.List;

/**  Strategy interface for ranking NOTAMs. */
public interface NotamPrioritizer {

    /**  Flight rules mode for prioritization logic. */
    enum Mode {
        IFR,
        VFR
    }

    /** Return a new list of NOTAMs sorted by descending priority score. */
    List<Notam> prioritize(List<Notam> notams);
}
