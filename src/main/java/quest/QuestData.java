package quest;

import com.shadowlegend.questplugin.QuestType;

public class QuestData {
    public final String title;
    public final QuestType type;
    public final String target;
    public final int goal;
    public final String reward;
    public final long duration;

    public QuestData(String title, QuestType type, String target, int goal, String reward, long duration) {
        this.title = title;
        this.type = type;
        this.target = target;
        this.goal = goal;
        this.reward = reward;
        this.duration = duration;
    }
}
