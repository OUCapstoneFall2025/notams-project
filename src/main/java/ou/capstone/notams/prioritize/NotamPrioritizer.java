package ou.capstone.notams.prioritize;

import ou.capstone.notams.Notam;
import java.util.List;

/** Strategy interface for ranking NOTAMs. */
public interface NotamPrioritizer {
    /** Return a new list of NOTAMs sorted by descending priority score. */
    List<Notam> prioritize(List<Notam> notams);
    
    double score(Notam notam);
}

