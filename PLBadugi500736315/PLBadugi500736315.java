import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PLBadugi500736315 implements PLBadugiPlayer {

    private static final double MAXAGGRO = 5;

    private Random rnd = new Random();
    private int position;
    private int[][] suitDistribution = new int[4][4];
    private double[][] suitDefaultProbabilities = {{0.015625, 0.00005, 0.64977, 1.0},{0.015625, 0.016505, 0.771204, 1.0},
            {0.001833, 0.186927, 0.895833, 1.0},{0.007319, 0.348600, 0.940659, 1.0}};
    private double[][] handDefaultProbabilities = {{1.000000,1.000000,0.999992,0.938842,0.788014,0.610866,0.355143,0.091855,0.075946,0.061351,0.046778,0.031554,0.015801},
            {1.000000,1.000000,0.997903,0.956716,0.857003,0.687420,0.450127,0.147172,0.073803,0.060864,0.046391,0.031486,0.015738},
            {1.000000,0.999299,0.981496,0.932941,0.845773,0.714636,0.539302,0.321166,0.247791,0.200827,0.153235,0.104705,0.052432},
            {1.000000,0.997794,0.979658,0.945443,0.895045,0.828842,0.746441,0.648747,0.556025,0.447862,0.325137,0.188307,0.096741}};
    private int[][] handDistribution = new int[4][13];
    private double[][] chanceDefaultProbabilities = {{0.4, 0.6, 0.90}, {0.2, 0.25, 0.80}, {0.2, 0.25, 0.80}, {0.2, 0.25, 0.80}};
    private int[][][][] resultStatistics = new int[4][2][4][13];
    private int[] winlose = new int[2];
    // State variables for the current hand, meaning exactly what the names say.
    private int ourDrawsRemaining;
    private int lastBetWasBluff = -1;
    private int lastDrawWasBluff = -1;
    private int weFoldedToRaise = -1;
    private int weRaisedLast = -1;
    private int ourLastDraw = 0;

    // How many hands have reached the given round against this opponent.
    private int[] handCount;
    // Our rough guesstimate of the chances for this hand.
    private double chances = 1.0;
    private int chanceSistribution[] = new int[100];
    // The name of this agent.
    private String name;
    // Chances multipliers depending on how many cards the opponent drew.
    private double[] drawMult = {1.1, 1.3, 1.5, 1.9};
    // Tracker of how aggressive the opponent has been on each drawing round.
    private int[] opponentAggro = new int[4];
    // Tracker of how aggressive we have been on each drawing round.
    private int[] ourAggro = new int[4];

    private static DecimalFormat df = new DecimalFormat();

    static {
        df.setMaximumFractionDigits(3);
    }
    // Counter of how many raymond objects have been created.
    private static int id = 0;

    public PLBadugi500736315(String name) {
        this.name = name;
    }

    public PLBadugi500736315() {
        this.name = "Raymond " + (++id);
    }

    // Threshold hands to estimate the value of our hand for the drawing rounds
    private PLBadugiHand[][] thresholds = {
            // Thresholds for 0 draws remaining
            {new PLBadugiHand("kh7h4c2d"), new PLBadugiHand("qh5s2h2d"), new PLBadugiHand("th9c4das"), new PLBadugiHand("7d6c3sah")},
            // Thresholds for 1 draws remaining
            {new PLBadugiHand("qh9d5c4c"), new PLBadugiHand("7s7c6c2d"), new PLBadugiHand("jh9h7s2c"), new PLBadugiHand("jd7c5s4h")},
            // Thresholds for 2 draws remaining
            {new PLBadugiHand("kstc7c2s"), new PLBadugiHand("js8s4d3d"), new PLBadugiHand("js6d4c2s"), new PLBadugiHand("ks7d4h2c")},
            // Thresholds for 3 draws remaining
            {new PLBadugiHand("khks7h2d"), new PLBadugiHand("Qs8h7s5c"), new PLBadugiHand("Th6h4s2c"), new PLBadugiHand("KhQsJdTc")}
    };

    /**
     * The method to inform the agent that a new heads-up match is starting.
     *
     * @param handsToGo How many hands this tournament consists of.
     */
    public void startNewMatch(int handsToGo) {
        handCount = new int[4];
        opponentAggro[0] = opponentAggro[1] = opponentAggro[2] = opponentAggro[3] = 0;
        ourAggro[0] = ourAggro[1] = ourAggro[2] = ourAggro[3] = 0;
    }

    /**
     * The method to inform the agent that the current heads-up match has ended.
     *
     * @param finalScore The total number of chips accumulated by this player during the match.
     */
    public void finishedMatch(int finalScore) {
//        priorProbabilities();
    }

    /**
     * The method to inform the agent that a new hand is starting.
     *
     * @param position     0 if the agent is the dealer in this hand, 1 if the opponent.
     * @param handsToGo    The number of hands left to play in this heads-up tournament.
     * @param currentScore The current score of the tournament.
     */
    public void startNewHand(int position, int handsToGo, int currentScore) {
        lastBetWasBluff = -1;
        lastDrawWasBluff = -1;
        weFoldedToRaise = -1;
        weRaisedLast = -1;
        chances = 1.0;
        this.position = position;
    }

    /**
     * The method to ask the agent what betting action it wants to perform.
     *
     * @param drawsRemaining How many draws are remaining after this betting round.
     * @param hand           The current hand held by this player.
     * @param pot            The current size of the pot.
     * @param raises         The number of raises made in this round.
     * @param toCall         The cost to call to stay in the pot.
     * @param minRaise       The minimum allowed raise to make, if the agent wants to raise.
     * @param maxRaise       The maximum allowed raise to make, if the agent wants to raise.
     * @param opponentDrew   How many cards the opponent drew in the previous drawing round. In the
     *                       first betting round, this argument will be -1.
     * @return The amount of chips that the player pushes into the pot. Putting in less than
     * toCall means folding. Any amount less than minRaise becomes a call, and any amount between
     * minRaise and maxRaise, inclusive, is a raise. Any amount greater than maxRaise is clipped at
     * maxRaise.
     */
    public int bettingAction(int drawsRemaining, PLBadugiHand hand, int pot, int raises, int toCall,
                             int minRaise, int maxRaise, int opponentDrew) {
        handCount[drawsRemaining]++;
        int suit = hand.getActiveCards().size() - 1;
        int rank = hand.getActiveCards().get(0).getRank() - 1;
        suitDistribution[drawsRemaining][suit]++;
        handDistribution[drawsRemaining][rank]++;

//        chances = posteriorChance(0, suit, rank)/(suitChance(drawsRemaining, suit) * handChance(drawsRemaining, rank));
         chances = suitChance(drawsRemaining, suit) * handChance(drawsRemaining, rank);
        if(chances > 1) {
            // System.out.printf("Chances %.3f.\n", chances);
        }
        chanceSistribution[(int)(chances*10)]++;
        if (chances > chanceDefaultProbabilities[drawsRemaining][2]) {
            return maxRaise;
        } else if (chances > chanceDefaultProbabilities[drawsRemaining][1]) {
            int amount = (int) (minRaise + (maxRaise - minRaise) * (chances));
            return amount;
        } else if (chances > chanceDefaultProbabilities[drawsRemaining][0]) {
            return toCall;
        } else {
            return 0;
        }
    }

    /**
     * The method to ask the agent which cards it wants to replace in this drawing round.
     *
     * @param drawsRemaining How many draws are remaining, including this drawing round.
     * @param hand           The current hand held by this player.
     * @param pot            The current size of the pot.
     * @param dealerDrew     How many cards the dealer drew in this drawing round. When this method is called
     *                       for the dealer, this argument will be -1.
     * @return The list of cards in the hand that the agent wants to replace.
     */
    public List<Card> drawingAction(int drawsRemaining, PLBadugiHand hand, int pot, int dealerDrew) {
        List<Card> allCards = hand.getAllCards();
        List<Card> inactiveCards = hand.getInactiveCards();
        List<Card> pitch = new ArrayList<Card>();

        // Pitch the inactive cards and also the active cards that are too high in rank.
        for (Card c : allCards) {
            if (c.getRank() > 10 - drawsRemaining || inactiveCards.contains(c)) {
                pitch.add(c);
            }
        }
        ourDrawsRemaining = drawsRemaining;
        return pitch;
    }

    /**
     * The method that gets called at the end of the current hand, whether fold or showdown.
     *
     * @param yourHand     The hand held by this agent.
     * @param opponentHand The hand held by the opponent, or null if either player folded.
     * @param result       The win or the loss in chips for the player.
     */
    public void handComplete(PLBadugiHand yourHand, PLBadugiHand opponentHand, int result) {
        lastBetWasBluff = -1;
        weRaisedLast = -1;
        weFoldedToRaise = -1;
        int suit = yourHand.getActiveCards().size() - 1;
        int rank = yourHand.getActiveCards().get(0).getRank() - 1;
        if(result >=0) {
            winlose[0] += result;
            resultStatistics[ourDrawsRemaining][0][suit][rank]++;
        } else {
            winlose[1] += result;
            resultStatistics[ourDrawsRemaining][1][suit][rank]++;
        }
    }

    /**
     * Returns the nickname of this agent.
     *
     * @return The nickname of this agent.
     */
    public String getAgentName() {
        return name;
    }

    /**
     * Returns the author of this agent. The name should be given in the format "Last, First".
     *
     * @return The author of this agent.
     */

    public String getAuthor() {
        return "Rui Zhang 500736315";
    }

    private double suitChance(int draw, int suit) {
        double frequency = 0;
        double total = 0;
        double probability = suitDefaultProbabilities[draw][suit];

        for (int i = 0; i < suitDistribution[draw].length; i++) {
            total += suitDistribution[draw][i];
            if (i <= suit) {
                frequency = total;
            }
        }

        if (handCount[draw]>100 && frequency > 0) {
            probability = frequency / total;
        }
        return probability;
    }

    private double handChance(int draw, int rank) {
        double frequency = 0;
        double total = 0;
        double probability = handDefaultProbabilities[draw][rank];

        for (int i = handDistribution[draw].length - 1; i >= 0 ; i--) {
            total += handDistribution[draw][i];
            if (i >= rank) {
                frequency = total;
            }
        }

        if (handCount[draw]>100 && frequency > 0) {
            probability = frequency / total;
        }
        return probability;
    }

    /***
     * posterior win propability
     * @param rank
     * @param suit
     * @return
     */
    private double posteriorChance(int draw, int suit, int rank)
    {
        double twin = 0;
        double tlose = 0;
        double probability = 0.001;//suitChance(draw, suit) * handChance(draw, rank) / 100;
        double total = 0;
        double win = 0;
        for(int i = 0; i < 2; i++ ) {
            for(int j = 0; j < 4; j++) {
                for (int k = 0; k < 13; k++) {
                    total += resultStatistics[draw][i][j][k];
                    if (i == 0 && (j < suit || (j==suit && k <= k))) {
                    //if (i == 0 && (j==suit && k == k)) {
                        win += resultStatistics[draw][i][j][k];
                    }
                    if(i==0) {
                        twin += resultStatistics[draw][i][j][k];
                    } else {
                        tlose += resultStatistics[draw][i][j][k];
                    }
                }
            }
        }
        if (win > 0) {
            probability = win / total;
        }
        return probability;
    }

    private void priorProbabilities() {
        posteriorChance(0, 3, 0);
        for (int i = 0; i < 4; i++) {
            System.out.printf("{");
            for (int j = 0; j < 4; j++) {
                System.out.printf("%f,", suitChance(i, j));
            }
            System.out.printf("},\n");
        }
        for (int i = 0; i < 4; i++) {
            System.out.printf("{");
            for (int j = 0; j < 13; j++) {
                System.out.printf("%f,", handChance(i, j));
            }
            System.out.printf("},\n ");
        }
    }
}
