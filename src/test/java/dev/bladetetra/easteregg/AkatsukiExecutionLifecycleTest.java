package dev.bladetetra.easteregg;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AkatsukiExecutionLifecycleTest {
    @Test
    void ChainKillMarksAnotherExecutionWithoutInvalidatingTickIterator() {
        var executions = new HashMap<UUID, AkatsukiExecution.Execution>();
        var first = execution();
        var second = execution();
        executions.put(first.targetId, first);
        executions.put(second.targetId, second);
        var iterator = executions.values().iterator();
        var current = iterator.next();
        var other = current == first ? second : first;
        other.observeDeath();
        assertEquals(2, executions.size());
        assertSame(other, assertDoesNotThrow(iterator::next));
        assertTrue(other.deathObserved);
        iterator.remove();
        assertEquals(1, executions.size());
    }

    @Test
    void RepeatedDeathNotificationIsIdempotent() {
        var execution = execution();
        execution.observeDeath();
        execution.observeDeath();
        assertTrue(execution.deathObserved);
    }

    private static AkatsukiExecution.Execution execution() {
        return new AkatsukiExecution.Execution(null, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 0, 0, 0, 0, 10, 1);
    }
}
