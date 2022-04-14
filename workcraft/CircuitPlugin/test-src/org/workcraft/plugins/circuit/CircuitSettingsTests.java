package org.workcraft.plugins.circuit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.workcraft.Config;
import org.workcraft.Framework;

class CircuitSettingsTests {

    @BeforeAll
    static void init() {
        final Framework framework = Framework.getInstance();
        framework.init();
        framework.resetConfig();
    }

    @Test
    void circuitSettingsTest() {
        final Framework framework = Framework.getInstance();
        String prefix = "CircuitSettings";

        Assertions.assertEquals(Config.toString(CircuitSettings.getShowContacts()),
                framework.getConfigVar(prefix + ".showContacts", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getContactFontSize()),
                framework.getConfigVar(prefix + ".contactFontSize", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getShowZeroDelayNames()),
                framework.getConfigVar(prefix + ".showZeroDelayNames", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getBorderWidth()),
                framework.getConfigVar(prefix + ".borderWidth", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getWireWidth()),
                framework.getConfigVar(prefix + ".wireWidth", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getActiveWireColor()),
                framework.getConfigVar(prefix + ".activeWireColor", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getInactiveWireColor()),
                framework.getConfigVar(prefix + ".inactiveWireColor", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getSimplifyStg()),
                framework.getConfigVar(prefix + ".simplifyStg", false));

        // Gate library
        Assertions.assertEquals(Config.toString(CircuitSettings.getGateLibrary()),
                framework.getConfigVar(prefix + ".gateLibrary", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getWaitData()),
                framework.getConfigVar(prefix + ".waitData", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getWait0Data()),
                framework.getConfigVar(prefix + ".wait0Data", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getMutexData()),
                framework.getConfigVar(prefix + ".mutexData", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getMutexLateSuffix()),
                framework.getConfigVar(prefix + ".mutexLateSuffix", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getMutexEarlySuffix()),
                framework.getConfigVar(prefix + ".mutexEarlySuffix", false));

        // Import/export
        Assertions.assertEquals(Config.toString(CircuitSettings.getExportSubstitutionLibrary()),
                framework.getConfigVar(prefix + ".exportSubstitutionLibrary", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getInvertExportSubstitutionRules()),
                framework.getConfigVar(prefix + ".invertExportSubstitutionRules", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getImportSubstitutionLibrary()),
                framework.getConfigVar(prefix + ".importSubstitutionLibrary", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getInvertImportSubstitutionRules()),
                framework.getConfigVar(prefix + ".invertImportSubstitutionRules", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getVerilogAssignDelay()),
                framework.getConfigVar(prefix + ".verilogAssignDelay", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getBusSuffix()),
                framework.getConfigVar(prefix + ".busSuffix", false));

        // Reset
        Assertions.assertEquals(Config.toString(CircuitSettings.getResetActiveHighPort()),
                framework.getConfigVar(prefix + ".resetActiveHighPort", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getResetActiveLowPort()),
                framework.getConfigVar(prefix + ".resetActiveLowPort", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getSetPin()),
                framework.getConfigVar(prefix + ".setPin", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getClearPin()),
                framework.getConfigVar(prefix + ".clearPin", false));

        // Scan
        Assertions.assertEquals(Config.toString(CircuitSettings.getTbufData()),
                framework.getConfigVar(prefix + ".tbufData", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getTinvData()),
                framework.getConfigVar(prefix + ".tinvData", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getScanSuffix()),
                framework.getConfigVar(prefix + ".scanSuffix", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getScaninData()),
                framework.getConfigVar(prefix + ".scaninData", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getScanoutData()),
                framework.getConfigVar(prefix + ".scanoutData", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getScanenData()),
                framework.getConfigVar(prefix + ".scanenData", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getScanckData()),
                framework.getConfigVar(prefix + ".scanckData", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getScantmData()),
                framework.getConfigVar(prefix + ".scantmData", false));

        Assertions.assertEquals(Config.toString(CircuitSettings.getIndividualScan()),
                framework.getConfigVar(prefix + ".individualScan", false));
    }

}
