package com.azentio.aml.alert;

import java.util.UUID;

public record AlertWriteResult(UUID alertId, boolean created) {
}
