package adx.variants.thirtydaysgame;

import adx.exceptions.AdXException;
import adx.structures.Campaign;
import adx.structures.MarketSegment;
import adx.structures.SimpleBidEntry;
import adx.util.Logging;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A thirty day game agent
 */
public class FiveAgent extends ThirtyDaysThirtyCampaignsAgent {
    public static final double ALPHA = 0.25;
    public static double GREED = 1.2;
    int consecutiveLosses = 0;
    double bidPerPerson = 2.5; // Initially we bid 2.5 for day 1.

    /**
     * Constructor.
     *
     * @param host
     * @param port
     */
    public FiveAgent(String host, int port) {
        super(host, port);
    }

    public static void main(String[] args) {
        FiveAgent agent = new FiveAgent("localhost", 9898);
        agent.connect("agent1");
    }

    @Override
    protected ThirtyDaysBidBundle getBidBundle(int day) {
        try {
            Campaign myCampaign;
            if (day <= 30) {
                Logging.log("[-] Bid for campaign " + day + " which is: " + this.setCampaigns[day - 1]);
                myCampaign = this.setCampaigns[day - 1];
            } else {
                throw new AdXException("[x] Bidding for invalid day " + day + ", bids in this game are only for day 1 or 2.");
            }

            if (myCampaign.getBudget() <= 0.1) {
                // previous bidding was lost
                consecutiveLosses += 1;
                // G' = G + (l - 1)^2 * alpha
                GREED = GREED + Math.pow(consecutiveLosses - 1, 2) * ALPHA;
            } else {
                // reset consec loss
                consecutiveLosses = 0;
                GREED = 1.2;
            }

            // A set to contains our bids for different segments
            Set<SimpleBidEntry> bidEntries = new HashSet<>();
            double budgetScalingfactor = getBudgetMultiple(myCampaign.getMarketSegment());

            List<MarketSegment> subsegments = new ArrayList<>();
            int total_users = 0;

            // Find the tooSpecific segments to bid on
            for (MarketSegment ms : threeQualifiedSegments()) {
                if (MarketSegment.marketSegmentSubset(myCampaign.getMarketSegment(), ms)) {
                    // ms is subset of our campaigns segment
                    total_users += MarketSegment.proportionsMap.get(ms); // add count of user in this subsegment
                    subsegments.add(ms);
                }
            }

            for (MarketSegment subsegment : subsegments) {
                // Proportion of bid to made to this sub segment
                double proportion = 1.0 * MarketSegment.proportionsMap.get(subsegment) / total_users;
                Logging.log("[-] proportion: " + proportion + " for " + subsegment);

                if (myCampaign.getBudget() > 0.1) {
                    // eg: Budget=100, reach=100, let bidPerPerson = 2.5;
                    bidEntries.add(new SimpleBidEntry(subsegment,
                            GREED * (myCampaign.getBudget() * bidPerPerson * budgetScalingfactor * proportion / (double) myCampaign.getReach()),
                            myCampaign.getBudget())); // FIXME: what should be the limit here?
                } else {
                    // we lost the previous bid; so bid taking a loss to improve qualityScore // TODO: do not bid if last day
                    bidEntries.add(new SimpleBidEntry(subsegment,
                            GREED * 1.0,
                            myCampaign.getReach())); // FIXME: what should be the limit here?
//                    bidEntries.add(new SimpleBidEntry(subsegment,
//                    GREED * (myCampaign.getBudget() * bidPerPerson * budgetScalingfactor * proportion / (double) myCampaign.getReach()),
//                            myCampaign.getReach())); // FIXME: what should be the limit here?
                }
            }

            Logging.log("[-] bidEntries = " + bidEntries);

            return new ThirtyDaysBidBundle(day, myCampaign.getId(),
                    myCampaign.getBudget(), bidEntries);

        } catch (AdXException e) {
            Logging.log("[x] Something went wrong getting the bid bundle: " + e.getMessage());
        }
        return null;
    }

    private Set<MarketSegment> threeQualifiedSegments() {
        Set<MarketSegment> s = new HashSet<>();
        s.add(MarketSegment.MALE_YOUNG_LOW_INCOME);
        s.add(MarketSegment.MALE_YOUNG_HIGH_INCOME);
        s.add(MarketSegment.MALE_OLD_LOW_INCOME);
        s.add(MarketSegment.MALE_OLD_HIGH_INCOME);
        s.add(MarketSegment.FEMALE_YOUNG_LOW_INCOME);
        s.add(MarketSegment.FEMALE_YOUNG_HIGH_INCOME);
        s.add(MarketSegment.FEMALE_OLD_LOW_INCOME);
        s.add(MarketSegment.FEMALE_OLD_HIGH_INCOME);
        return s;
    }

    private Set<MarketSegment> twoQualifiedSegments() {
        Set<MarketSegment> s = new HashSet<>();
        s.add(MarketSegment.YOUNG_LOW_INCOME);
        s.add(MarketSegment.YOUNG_HIGH_INCOME);
        s.add(MarketSegment.OLD_LOW_INCOME);
        s.add(MarketSegment.OLD_HIGH_INCOME);
        s.add(MarketSegment.MALE_LOW_INCOME);
        s.add(MarketSegment.MALE_HIGH_INCOME);
        s.add(MarketSegment.FEMALE_LOW_INCOME);
        s.add(MarketSegment.FEMALE_HIGH_INCOME);
        s.add(MarketSegment.MALE_YOUNG);
        s.add(MarketSegment.MALE_OLD);
        s.add(MarketSegment.FEMALE_YOUNG);
        s.add(MarketSegment.FEMALE_OLD);
        return s;
    }

    Set<MarketSegment> oneQualifiedSegments() {
        Set<MarketSegment> s = new HashSet<>();
        s.add(MarketSegment.LOW_INCOME);
        s.add(MarketSegment.HIGH_INCOME);
        s.add(MarketSegment.YOUNG);
        s.add(MarketSegment.OLD);
        s.add(MarketSegment.MALE);
        s.add(MarketSegment.FEMALE);
        return s;
    }

    private double getBudgetMultiple(MarketSegment s) {
        double constant = 2.0;
        // Can only bid for 1 segment
        if (threeQualifiedSegments().contains(s)) {
            // Can bid for only 3 qualified segments
            return 1 * constant;
        }
        if (twoQualifiedSegments().contains(s)) {
            // Can bid for 2 possible subsegments
            // eg if s= (M,Y) ; we bid for (M,Y,LI); (M,Y,HI)
            return 2 * constant;
        }

        // can bid for 4 possible segments
        // eg if s=Male; we bid for M,Y,LI; M,Y,HI; M,O,LI; M,O,HI
        return 4 * constant;
    }
}
