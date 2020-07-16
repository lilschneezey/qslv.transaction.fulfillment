package qslv.transaction.fulfillment;

import org.junit.platform.runner.JUnitPlatform;
import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
@SelectPackages("qslv.transaction.fulfillment")
@IncludeClassNamePatterns("^(Unit_.*|.+[.$]Unit_.*)$")
class UnitSuiteTransactionTest {


}
