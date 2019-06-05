package hk.polyu.comp.jaid.monitor.suspiciouness;

import hk.polyu.comp.jaid.fixer.log.LoggingService;

public abstract class AbsSuspiciousnessAlgorithm {
    SbflAlgorithm sbflAlgorithm;

    protected int coveredFailingTestCout;//number of failed test cases that cover a statement
    protected int uncoveredFailingTestCount;//number of failed test cases that do not cover a statement
    protected int coveredPassingTestCount;//number of successful test cases that cover a statement
    protected int uncoveredPassingTestCount;//number of successful test cases that do not cover a statement
    protected int coveredTestCount;//total number of test cases that cover a statement
    protected int uncoveredTestCount;//total number of test cases that do not cover a statement
    protected int passingTestCount;//total number of successful test cases
    protected int failingTestCount;//total number of failed test cases
    protected int totalTestCount;//total number of all test cases

    public AbsSuspiciousnessAlgorithm(SbflAlgorithm sbflAlgorithm, int totalTestCount, int passingTestCount) {
        this.sbflAlgorithm = sbflAlgorithm;

        this.totalTestCount = totalTestCount;
        this.passingTestCount = passingTestCount;
    }


    public static AbsSuspiciousnessAlgorithm construct(int totalTestCount, int passingTestCount, SbflAlgorithm sbflAlgorithm) {
        LoggingService.infoAll("sbflAlgorithm ::" + sbflAlgorithm.toString());

        switch (sbflAlgorithm) {
            case AutoFix:
                return new AutoFixSusAlgorithm(sbflAlgorithm, totalTestCount, passingTestCount);
            case AFNoDup:
                return new AFNoDupSusAlgorithm(sbflAlgorithm, totalTestCount, passingTestCount);
            case AFFormula:
                return new AFFormulaSusAlgorithm(sbflAlgorithm, totalTestCount, passingTestCount);
            case AFNoDupFormula:
                return new AFNoDupFormulaSusAlgorithm(sbflAlgorithm, totalTestCount, passingTestCount);
            case Op2:
                return new Op2SusAlgorithm(sbflAlgorithm, totalTestCount, passingTestCount);
            case DStar:
                return new DStarSusAlgorithm(sbflAlgorithm, totalTestCount, passingTestCount);
            case Ochiai:
                return new OchiaiSusAlgorithm(sbflAlgorithm, totalTestCount, passingTestCount);
            case Barinel:
                return new BarinelSusAlgorithm(sbflAlgorithm, totalTestCount, passingTestCount);
            case Tarantula:
                return new TarantulaSusAlgorithm(sbflAlgorithm, totalTestCount, passingTestCount);

        }
        throw new IllegalArgumentException("Selected SBFL Algorithm is: " + sbflAlgorithm.toString());
    }

    private void initValue(int coveredFailingTestCout, int coveredPassingTestCount) {
        this.coveredFailingTestCout = coveredFailingTestCout;
        this.coveredPassingTestCount = coveredPassingTestCount;
        //compute other value
        this.failingTestCount = this.totalTestCount - this.passingTestCount;
        this.uncoveredFailingTestCount = this.failingTestCount - coveredFailingTestCout;
        this.uncoveredPassingTestCount = this.passingTestCount - coveredPassingTestCount;
        this.coveredTestCount = coveredFailingTestCout + coveredPassingTestCount;
        this.uncoveredTestCount = this.totalTestCount - this.coveredTestCount;
    }

    public double computeSuspiciousness(int coveredFailingTestCout, int coveredPassingTestCount, double distanceToFailure, double similarityToLocation) {
        initValue(coveredFailingTestCout, coveredPassingTestCount);
        return executableStatementHitSpectrumAlgorithm(distanceToFailure, similarityToLocation);
    }


    public abstract double executableStatementHitSpectrumAlgorithm(double distanceToFailure, double similarityToLocation);

    public static final double MINIMUM_SIMILARITY = 1;


    public SbflAlgorithm getSbflAlgorithm() {
        return sbflAlgorithm;
    }

    public enum SbflAlgorithm {
        AutoFix, AFNoDup, AFFormula, AFNoDupFormula, Barinel, DStar, Ochiai, Op2, Tarantula;

        public static SbflAlgorithm get(String algorithm) {
            if (algorithm.equals(AutoFix.name())) return AutoFix;
            else if (algorithm.equals(AFNoDup.name())) return AFNoDup;
            else if (algorithm.equals(AFFormula.name())) return AFFormula;
            else if (algorithm.equals(AFNoDupFormula.name())) return AFNoDupFormula;
            else if (algorithm.equals(Barinel.name())) return Barinel;
            else if (algorithm.equals(DStar.name())) return DStar;
            else if (algorithm.equals(Ochiai.name())) return Ochiai;
            else if (algorithm.equals(Op2.name())) return Op2;
            else if (algorithm.equals(Tarantula.name())) return Tarantula;
            throw new IllegalArgumentException("Selected SBFL Algorithm is: " + algorithm);
        }
    }


    public static class AutoFixSusAlgorithm extends AbsSuspiciousnessAlgorithm {

        protected AutoFixSusAlgorithm(SbflAlgorithm sbflAlgorithm, int totalTestCount, int passingTestCount) {
            super(sbflAlgorithm, totalTestCount, passingTestCount);
        }

        @Override
        public double executableStatementHitSpectrumAlgorithm(double distanceToFailure, double similarityToLocation) {
            double hitSpectrum = GAMMA + ALPHA / (1 - ALPHA) * (1 - BETA + BETA * Math.pow(ALPHA, coveredPassingTestCount) - Math.pow(ALPHA, coveredFailingTestCout));
            //        double distanceContribution = 1 - distanceToFailure / (LineLocation.getMaximumDistanceToFailure() + 1);
            double similarityContribution = MINIMUM_SIMILARITY + similarityToLocation;

            //        return 3.0 / (1 / executableStatementHitSpectrumAlgorithm() + 1 / distanceContribution + 1 / similarityContribution);
            return 2.0 / (1 / hitSpectrum + 1 / similarityContribution);
        }


        public static final double ALPHA = 1.0 / 3;
        public static final double BETA = 2.0 / 3;
        public static final double GAMMA = 1.0;


    }

    public static class AFNoDupSusAlgorithm extends AutoFixSusAlgorithm {

        protected AFNoDupSusAlgorithm(SbflAlgorithm sbflAlgorithm, int totalTestCount, int passingTestCount) {
            super(sbflAlgorithm, totalTestCount, passingTestCount);
        }

    }

    public static class AFFormulaSusAlgorithm extends AbsSuspiciousnessAlgorithm {

        protected AFFormulaSusAlgorithm(SbflAlgorithm sbflAlgorithm, int totalTestCount, int passingTestCount) {
            super(sbflAlgorithm, totalTestCount, passingTestCount);
        }

        @Override
        public double executableStatementHitSpectrumAlgorithm(double distanceToFailure, double similarityToLocation) {
            double hitSpectrum = GAMMA + ALPHA / (1 - ALPHA) * (1 - BETA + BETA * Math.pow(ALPHA, coveredPassingTestCount) - Math.pow(ALPHA, coveredFailingTestCout));
            return hitSpectrum;
        }

        public static final double ALPHA = 1.0 / 3;
        public static final double BETA = 2.0 / 3;
        public static final double GAMMA = 1.0;


    }

    public static class AFNoDupFormulaSusAlgorithm extends AFFormulaSusAlgorithm {

        protected AFNoDupFormulaSusAlgorithm(SbflAlgorithm sbflAlgorithm, int totalTestCount, int passingTestCount) {
            super(sbflAlgorithm, totalTestCount, passingTestCount);
        }

    }


    public static class BarinelSusAlgorithm extends AbsSuspiciousnessAlgorithm {

        protected BarinelSusAlgorithm(SbflAlgorithm sbflAlgorithm, int totalTestCount, int passingTestCount) {
            super(sbflAlgorithm, totalTestCount, passingTestCount);
        }

        @Override// if the denominators evaluate to zero, return 0.
        public double executableStatementHitSpectrumAlgorithm(double distanceToFailure, double similarityToLocation) {
            if (coveredPassingTestCount + coveredFailingTestCout == 0) return 0;
            return 1 - (coveredPassingTestCount / (double) (coveredPassingTestCount + coveredFailingTestCout));
        }

    }

    public static class DStarSusAlgorithm extends AbsSuspiciousnessAlgorithm {

        protected DStarSusAlgorithm(SbflAlgorithm sbflAlgorithm, int totalTestCount, int passingTestCount) {
            super(sbflAlgorithm, totalTestCount, passingTestCount);
        }

        @Override
// if the denominators evaluate to zero, return totalTestCount (this will happen only when coveredFail==totalFail and coveredPass==0).
        public double executableStatementHitSpectrumAlgorithm(double distanceToFailure, double similarityToLocation) {
            if (coveredPassingTestCount + (failingTestCount - coveredFailingTestCout) == 0)
                return Math.pow(totalTestCount, 2);//todo:check whether the return score is maximum
            return Math.pow(coveredFailingTestCout, 2) / (coveredPassingTestCount + (failingTestCount - coveredFailingTestCout));
        }

    }

    public static class OchiaiSusAlgorithm extends AbsSuspiciousnessAlgorithm {

        protected OchiaiSusAlgorithm(SbflAlgorithm sbflAlgorithm, int totalTestCount, int passingTestCount) {
            super(sbflAlgorithm, totalTestCount, passingTestCount);
        }

        @Override // if the denominators evaluate to zero, return 0.
        public double executableStatementHitSpectrumAlgorithm(double distanceToFailure, double similarityToLocation) {
            if (Math.sqrt(failingTestCount * (coveredFailingTestCout + coveredPassingTestCount)) == 0) return 0;
            return coveredFailingTestCout / Math.sqrt(failingTestCount * (coveredFailingTestCout + coveredPassingTestCount));
        }

    }

    public static class Op2SusAlgorithm extends AbsSuspiciousnessAlgorithm {

        protected Op2SusAlgorithm(SbflAlgorithm sbflAlgorithm, int totalTestCount, int passingTestCount) {
            super(sbflAlgorithm, totalTestCount, passingTestCount);
        }

        @Override //the denominators never evaluate to zero
        public double executableStatementHitSpectrumAlgorithm(double distanceToFailure, double similarityToLocation) {
            return coveredFailingTestCout - (coveredPassingTestCount / (double) (passingTestCount + 1));
        }

    }

    public static class TarantulaSusAlgorithm extends AbsSuspiciousnessAlgorithm {

        protected TarantulaSusAlgorithm(SbflAlgorithm sbflAlgorithm, int totalTestCount, int passingTestCount) {
            super(sbflAlgorithm, totalTestCount, passingTestCount);
        }

        @Override // Note that if any of the denominators evaluate to zero, we assign zero to that fraction.
        public double executableStatementHitSpectrumAlgorithm(double distanceToFailure, double similarityToLocation) {
            double fraction1 = 0, fraction2 = 0;
            if (failingTestCount != 0) {
                fraction1 = coveredFailingTestCout / (double) failingTestCount;
            }
            if (passingTestCount != 0) {
                fraction2 = coveredPassingTestCount / (double) passingTestCount;
            }
            if (fraction1 + fraction2 == 0)
                return 0;
            return fraction1 / (fraction1 + fraction2);

//            if (failingTestCount == 0 || coveredFailingTestCout == 0) return 0;
//            if (passingTestCount == 0) return 1;
//            return (coveredFailingTestCout / failingTestCount) / (coveredFailingTestCout / failingTestCount + coveredPassingTestCount / passingTestCount);
        }

    }
}
