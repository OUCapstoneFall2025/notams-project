package ou.capstone.notams.prioritize;

import java.util.List;

import ou.capstone.notams.Notam;

/** Strategy interface for ranking NOTAMs. */
public interface NotamPrioritizer {

    /** IFR / VFR flight mode. */
    enum Mode {
        IFR, VFR
    }

    /** Return a new list of NOTAMs sorted by descending priority score. */
    List<Notam> prioritize(List<Notam> notams);
    
    double score(Notam notam);
}

