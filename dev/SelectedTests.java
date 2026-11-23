/** Ruft die statische Methode run() jeder als Argument genannten Testklasse auf (siehe dev/run-tests.sh). */
public final class SelectedTests
{
    public static void main(String[] args) throws Exception
    {
        for (String name : args)
        {
            Class.forName(name).getMethod("run").invoke(null);
            System.out.println("PASS " + name);
        }
    }
}
