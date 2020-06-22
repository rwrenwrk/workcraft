package org.workcraft.plugins.cpog.sat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.workcraft.formula.cnf.Cnf;
import org.workcraft.plugins.cpog.CpogSettings;
import org.workcraft.plugins.cpog.encoding.onehot.CnfGeneratingOptimiser;

@Disabled // This only works with MINISAT solver which is not supported in Travis OSX
public class CnfSolverTests extends SolverTests {

    @BeforeAll
    public static void setSatSolver() {
        CpogSettings.setSatSolver(CpogSettings.SatSolver.MINISAT);
    }

    @Override
    protected LegacySolver<Cnf> createSolver() {
        return new LegacySolver<>(
                new CnfGeneratingOptimiser(),
                new SimpleCnfTaskProvider());
    }

}