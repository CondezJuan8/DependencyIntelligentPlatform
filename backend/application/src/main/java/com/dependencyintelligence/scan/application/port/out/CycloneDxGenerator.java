package com.dependencyintelligence.scan.application.port.out;

import java.io.InputStream;

/** Generates a CycloneDX SBOM from one authorized repository snapshot. */
public interface CycloneDxGenerator {
    byte[] generate(InputStream repositoryArchive, Ecosystem ecosystem);

    enum Ecosystem { MAVEN, NPM }
}
