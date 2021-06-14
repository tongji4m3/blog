import java.util.LinkedList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.*;

public class MainTest {
    public static void main(String[] args) throws InterruptedException {
        int[] ints = {3, 3, 1, 3};
        new MainTest().minArray(ints);

        LinkedList<Integer> linkedList = new LinkedList<>();

        linkedList.stream().mapToInt(Integer::valueOf).toArray()
        Integer[] integers = linkedList.toArray(new Integer[3]);

        Thread

    }

    public int minArray(int[] numbers) {
        int lo = 0, hi = numbers.length - 1;
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (numbers[mid] <= numbers[hi]) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return numbers[hi];
    }
}
