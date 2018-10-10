package de.tum.bgu.msm.data.person;

public class PersonFactoryImpl implements PersonFactory {

    @Override
    public Person createPerson(int id, int age, Gender gender, Race race, Occupation occupation, int workplace, int income) {
        return new PersonImpl(id, age, gender, race, occupation, workplace, income);
    }
}