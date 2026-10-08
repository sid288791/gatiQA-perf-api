package org.gatiqa.gatiqaperfapi.perfrun.service;

import java.util.UUID;

public class PerfRunNotFoundException extends RuntimeException {

    public PerfRunNotFoundException(UUID id) {
        super("Performance run not found: " + id);
    }
}
