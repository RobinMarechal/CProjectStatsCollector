public class Solution
{
    private final int score;
    private final double time;
    private final int nbIt;

    public Solution (int score, double time, int nbIt)
    {
        this.score = score;
        this.time = time;
        this.nbIt = nbIt;
    }

    public int getScore ()
    {
        return score;
    }

    public double getTime ()
    {
        return time;
    }

    public int getNbIt ()
    {
        return nbIt;
    }
}
