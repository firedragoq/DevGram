package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import org.telegram.messenger.DevGramPlugins;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import java.util.ArrayList;

public class DevGramPluginHistoryActivity extends BaseFragment {
    private final String pluginId, pluginName;
    private final ArrayList<DevGramPlugins.HistoryEntry> history = new ArrayList<>();
    private UniversalRecyclerView list;
    public DevGramPluginHistoryActivity(String id, String name) { pluginId = id; pluginName = name; }
    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back); actionBar.setTitle("История: " + pluginName);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){ @Override public void onItemClick(int id){ if(id==-1) finishFragment(); }});
        FrameLayout root = new FrameLayout(context); list = new UniversalRecyclerView(this, this::fill, null, null);
        list.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider)); root.addView(list, LayoutHelper.createFrame(-1,-1, Gravity.FILL));
        DevGramPlugins.fetchPluginHistory(pluginId, items -> { history.clear(); history.addAll(items); list.adapter.update(true); });
        return fragmentView = root;
    }
    private void fill(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("История публикации"));
        if(history.isEmpty()) { items.add(UItem.asShadow("История пока пуста.")); return; }
        for(DevGramPlugins.HistoryEntry h: history) {
            String title;
            switch(h.action){ case "submitted": title="Заявка отправлена"; break; case "update_submitted": title="Обновление отправлено"; break; case "approved": title="Одобрено"; break; case "rejected": title="Отклонено"; break; case "rejected_blocked": title="Отклонено и заблокировано"; break; default: title=h.action; }
            String sub=(h.actorName.isEmpty()?String.valueOf(h.actorId):h.actorName)+" · "+android.text.format.DateFormat.format("dd.MM.yyyy HH:mm",h.date)+(h.details.isEmpty()?"":"\n"+h.details);
            items.add(UItem.asButton(h.hashCode(), R.drawable.msg_info, title, sub));
        }
        items.add(UItem.asShadow(null));
    }
}
