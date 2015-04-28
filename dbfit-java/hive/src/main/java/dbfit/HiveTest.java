package dbfit;

public class HiveTest extends DatabaseTest {
    public HiveTest() {
        super(dbfit.api.DbEnvironmentFactory.newEnvironmentInstance("Hive"));
    }
}

