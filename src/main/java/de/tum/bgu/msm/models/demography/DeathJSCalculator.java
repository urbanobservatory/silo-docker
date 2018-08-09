package de.tum.bgu.msm.models.demography;

import de.tum.bgu.msm.data.Person;
import de.tum.bgu.msm.util.js.JavaScriptCalculator;

import java.io.Reader;

public class DeathJSCalculator extends JavaScriptCalculator <Double> {

    public DeathJSCalculator (Reader reader) {
        super(reader);
    }

    public double calculateDeathProbability(int personAge, Person.Gender personSex) {
        return super.calculate("calculateDeathProbability", personAge, personSex);
    }

}