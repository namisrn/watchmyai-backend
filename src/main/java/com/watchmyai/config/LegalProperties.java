package com.watchmyai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "watchmyai.legal")
public class LegalProperties {

    private final String contactEmail;

    // Impressum / Anbieterkennzeichnung nach § 5 DDG (Digitale-Dienste-Gesetz,
    // ehem. § 5 TMG). Werden über Umgebungsvariablen befüllt — solange leer,
    // rendert /impressum die jeweilige Zeile schlicht nicht. Vor Go-Live mit
    // echten Daten setzen (siehe legal/IMPRESSUM.md).
    private final String operatorName;
    private final String operatorAddress;
    private final String operatorPostalCity;
    private final String operatorCountry;
    private final String vatId;

    public LegalProperties(
            String contactEmail,
            String operatorName,
            String operatorAddress,
            String operatorPostalCity,
            String operatorCountry,
            String vatId
    ) {
        this.contactEmail = contactEmail;
        this.operatorName = operatorName;
        this.operatorAddress = operatorAddress;
        this.operatorPostalCity = operatorPostalCity;
        this.operatorCountry = operatorCountry;
        this.vatId = vatId;
    }

    public String contactEmail() {
        return contactEmail;
    }

    public String operatorName() {
        return operatorName;
    }

    public String operatorAddress() {
        return operatorAddress;
    }

    public String operatorPostalCity() {
        return operatorPostalCity;
    }

    public String operatorCountry() {
        return operatorCountry;
    }

    public String vatId() {
        return vatId;
    }
}
