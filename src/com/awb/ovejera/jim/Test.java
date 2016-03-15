package com.awb.ovejera.jim;

/**
 * Created by Cyril Agahan on 3/11/2016.
 */
public class Test
{
    public static void main(String args[]){
        new Test();
    }

    public Test(){
        System.out.println(Test.class.getResource("/res/awb-logo.png"));
    }
}
