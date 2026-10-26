# Generates a synthetic Java project for timing the tsDetect Eclipse plugin.
#
# Creates N production/test file pairs following the naming convention the
# plugin requires (testExample.java pairs with Example.java). Real-world
# projects use the ExampleTest.java convention, which the plugin cannot pair,
# so a synthetic corpus is used for timing instead.
#
# Usage:   .\generate-perf-fixture.ps1 -Pairs 100 -OutDir C:\dev\PerfFixtureSrc

param(
    [int]$Pairs = 100,
    [string]$OutDir = "C:\dev\PerfFixtureSrc"
)

if (Test-Path $OutDir) { Remove-Item -Recurse -Force $OutDir }
New-Item -ItemType Directory -Path $OutDir | Out-Null

for ($i = 1; $i -le $Pairs; $i++) {

    $prod = @"
public class Widget$i {
    public int add(int a, int b) { return a + b; }
    public int subtract(int a, int b) { return a - b; }
    public String describe() { return "Widget$i"; }
}
"@
    Set-Content -Path (Join-Path $OutDir "Widget$i.java") -Value $prod -Encoding UTF8

    $test = @"
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class testWidget$i {

    private Widget$i widget;
    private String neverUsed;

    @Before
    public void setUp() {
        widget = new Widget$i();
        neverUsed = "unused";
    }

    public testWidget$i() {
        widget = new Widget$i();
    }

    @Test
    public void testAddition() {
        assertEquals(4, widget.add(2, 2));
        assertEquals(6, widget.add(3, 3));
    }

    @Test
    public void testWithPrint() {
        System.out.println("running");
        assertTrue(widget.add(1, 1) == 2);
    }

    @Test
    public void testSleepy() throws InterruptedException {
        Thread.sleep(1);
        assertNotNull(widget);
    }

    @Test
    public void testEmptyBody() {
    }

    @Test
    public void testEager() {
        assertEquals(4, widget.add(2, 2));
        assertEquals(0, widget.subtract(2, 2));
        assertEquals("Widget$i", widget.describe().toString());
    }
}
"@
    Set-Content -Path (Join-Path $OutDir "testWidget$i.java") -Value $test -Encoding UTF8
}

$count = (Get-ChildItem $OutDir -Filter *.java).Count
Write-Host "Generated $Pairs pairs ($count files) in $OutDir"
