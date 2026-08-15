"""Account-aware Telegram helpers for DevGram plugins."""
from devgram import get_client, get_selected_account, AccountClient
from java import dynamic_proxy, jclass

_Plugins = jclass("org.telegram.messenger.DevGramPlugins")
_NotificationDelegate = jclass(
    "org.telegram.messenger.NotificationCenter$NotificationCenterDelegate")


class NotificationCenterDelegate(dynamic_proxy(_NotificationDelegate)):
    """Subclass and override didReceivedNotification(id, account, args)."""
    def didReceivedNotification(self, notification_id, account, args):
        pass


class ObserverHandle:
    def __init__(self, center, delegate, notification_id):
        self.center, self.delegate, self.notification_id = center, delegate, int(notification_id)
        self.active = True

    def close(self):
        if not self.active:
            return False
        self.center.removeObserver(self.delegate, self.notification_id)
        self.active = False
        return True

    unobserve = close

def send_text(peer, text, account=None): get_client(account).send_text(peer, text)
def send_formatted_text(peer, text, entities, account=None):
    from devgram.text_formatting import to_tlrpc_entities
    _Plugins.sendFormattedText(_account(account), int(peer), str(text), to_tlrpc_entities(entities))
def get_messages_controller(account=None): return get_client(account).get_messages_controller()
def get_user_config(account=None): return get_client(account).get_user_config()
def get_connections_manager(account=None): return get_client(account).get_connections_manager()
def get_account_instance(account=None): return _Plugins.accountInstance(_account(account))
def get_send_messages_helper(account=None): return _Plugins.sendMessagesHelper(_account(account))
def get_media_data_controller(account=None): return _Plugins.mediaDataController(_account(account))
def get_contacts_controller(account=None): return _Plugins.contactsController(_account(account))
def get_messages_storage(account=None): return _Plugins.messagesStorage(_account(account))
def get_notification_center(account=None): return _Plugins.notificationCenter(_account(account))
def get_file_loader(account=None): return _Plugins.fileLoader(_account(account))
def get_media_controller(): return _Plugins.mediaController()
def get_notifications_controller(account=None): return _Plugins.notificationsController(_account(account))
def get_notifications_settings(account=None): return _Plugins.notificationsSettings(_account(account))
def get_location_controller(account=None): return _Plugins.locationController(_account(account))
def get_secret_chat_helper(account=None): return _Plugins.secretChatHelper(_account(account))
def get_download_controller(account=None): return _Plugins.downloadController(_account(account))
def get_last_fragment(): return jclass("org.telegram.ui.LaunchActivity").getSafeLastFragment()


def observe(notification_id, callback, account=None):
    """Observe one NotificationCenter event and return a removable ObserverHandle."""
    center = get_notification_center(account)
    class _CallbackDelegate(NotificationCenterDelegate):
        def didReceivedNotification(self, notification_id_value, account_value, args):
            callback(int(notification_id_value), int(account_value), args)
    delegate = _CallbackDelegate()
    center.addObserver(delegate, int(notification_id))
    return ObserverHandle(center, delegate, notification_id)


def _account(account):
    return int(get_selected_account() if account is None else account)
