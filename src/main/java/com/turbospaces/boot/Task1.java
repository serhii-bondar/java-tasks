package com.turbospaces.boot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

//  Implement method doInParallel in a way it takes a list of tasks and runs them in parallel.
//  Print a message in async manner when all tasks are completed.
public class Task1 {
    public static void main(String[] args) {
        System.out.println("Starting doing in parallel...");

        // create a list of tasks
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final int j = i;
            tasks.add(() -> {
                System.out.println("Task " + j + " started");
                try {
                    Thread.sleep(new Random().nextInt(1000));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("Task " + j + " finished");
            });
        }

        // call method that runs tasks in parallel
        doInParallel(tasks);

        System.out.println("Continue working on smth else...");
    }

    public static void doInParallel(List<Runnable> tasks) {
    }
}
