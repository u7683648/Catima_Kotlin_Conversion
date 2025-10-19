package protect.card_locker

import android.content.DialogInterface
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher.addCallback
import androidx.activity.OnBackPressedDispatcher.onBackPressed
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import protect.card_locker.LoyaltyCardCursorAdapter.CardAdapterListener
import protect.card_locker.databinding.ActivityManageGroupBinding

class ManageGroupActivity : CatimaAppCompatActivity(), CardAdapterListener {
    private var binding: ActivityManageGroupBinding? = null
    private var mDatabase: SQLiteDatabase? = null
    private lateinit var mAdapter: ManageGroupCursorAdapter

    private val SAVE_INSTANCE_ADAPTER_STATE = "adapterState"
    private val SAVE_INSTANCE_CURRENT_GROUP_NAME = "currentGroupName"

    protected var mGroup: Group? = null
    private var mCardList: RecyclerView? = null
    private var noGroupCardsText: TextView? = null
    private var mGroupNameText: EditText? = null

    private var mGroupNameNotInUse = false

    override fun onCreate(inputSavedInstanceState: Bundle?) {
        super.onCreate(inputSavedInstanceState)
        binding = ActivityManageGroupBinding.inflate(getLayoutInflater())
        setContentView(binding!!.getRoot())
        Utils.applyWindowInsetsAndFabOffset(binding!!.getRoot(), binding!!.fabSave)
        val toolbar: Toolbar = binding!!.toolbar
        setSupportActionBar(toolbar)

        mDatabase = DBHelper(this).getWritableDatabase()

        noGroupCardsText = binding!!.include.noGroupCardsText
        mCardList = binding!!.include.list
        val saveButton = binding!!.fabSave

        mGroupNameText = binding!!.editTextGroupName

        mGroupNameText!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                mGroupNameNotInUse = true
                mGroupNameText!!.setError(null)
                val currentGroupName = mGroupNameText!!.getText().toString().trim { it <= ' ' }
                if (currentGroupName.length == 0) {
                    mGroupNameText!!.setError(getResources().getText(R.string.group_name_is_empty))
                    return
                }
                if (mGroup!!._id != currentGroupName) {
                    if (DBHelper.getGroup(mDatabase, currentGroupName) != null) {
                        mGroupNameNotInUse = false
                        mGroupNameText!!.setError(getResources().getText(R.string.group_name_already_in_use))
                    } else {
                        mGroupNameNotInUse = true
                    }
                }
            }
        })

        val intent = getIntent()
        val groupId = intent.getStringExtra("group")
        if (groupId == null) {
            throw (IllegalArgumentException("this activity expects a group loaded into it's intent"))
        }
        Log.d("groupId", "groupId: " + groupId)
        mGroup = DBHelper.getGroup(mDatabase, groupId)
        if (mGroup == null) {
            throw (IllegalArgumentException("cannot load group " + groupId + " from database"))
        }
        mGroupNameText!!.setText(mGroup!!._id)
        setTitle(getString(R.string.editGroup, mGroup!!._id))
        mAdapter = ManageGroupCursorAdapter(this, null, this, mGroup, null)
        mCardList!!.setAdapter(mAdapter)
        registerForContextMenu(mCardList)

        if (inputSavedInstanceState != null) {
            mAdapter!!.importInGroupState(
                integerArrayToAdapterState(
                    inputSavedInstanceState.getIntegerArrayList(
                        SAVE_INSTANCE_ADAPTER_STATE
                    )!!
                )
            )
            mGroupNameText!!.setText(
                inputSavedInstanceState.getString(
                    SAVE_INSTANCE_CURRENT_GROUP_NAME
                )
            )
        }

        enableToolbarBackButton()

        saveButton.setOnClickListener(View.OnClickListener { v: View? ->
            val currentGroupName = mGroupNameText!!.getText().toString().trim { it <= ' ' }
            if (currentGroupName != mGroup!!._id) {
                if (currentGroupName.length == 0) {
                    Toast.makeText(
                        getApplicationContext(),
                        R.string.group_name_is_empty,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                if (!mGroupNameNotInUse) {
                    Toast.makeText(
                        getApplicationContext(),
                        R.string.group_name_already_in_use,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
            }

            mAdapter!!.commitToDatabase()
            if (currentGroupName != mGroup!!._id) {
                DBHelper.updateGroup(mDatabase, mGroup!!._id, currentGroupName)
            }
            Toast.makeText(getApplicationContext(), R.string.group_updated, Toast.LENGTH_SHORT)
                .show()
            finish()
        })
        // this setText is here because content_main.xml is reused from main activity
        noGroupCardsText!!.setText(getResources().getText(R.string.noGiftCardsGroup))
        updateLoyaltyCardList()

        getOnBackPressedDispatcher().addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                leaveWithoutSaving()
            }
        })
    }

    private fun adapterStateToIntegerArray(adapterState: HashMap<Int, Boolean>): ArrayList<Int> {
        val ret = ArrayList<Int>(adapterState.size * 2)
        for ((key, value) in adapterState) {
            ret += key
            ret += if (value) 1 else 0
        }
        return ret
    }

    private fun integerArrayToAdapterState(list: ArrayList<Int>): HashMap<Int, Boolean> {
        require(list.size % 2 == 0) { "failed restoring adapterState from integer array list" }

        val ret = HashMap<Int, Boolean>()
        for (i in list.indices step 2) {
            ret[list[i]] = list[i+1] == 1
        }
        return ret
    }

    override fun onCreateOptionsMenu(inputMenu: Menu?): Boolean {
        menuInflater.inflate(R.menu.card_details_menu, inputMenu)

        return super.onCreateOptionsMenu(inputMenu)
    }

    override fun onOptionsItemSelected(inputItem: MenuItem): Boolean {
        val id = inputItem.itemId

        if (id == R.id.action_display_options) {
            mAdapter.showDisplayOptionsDialog()
            invalidateOptionsMenu()

            return true
        }

        return super.onOptionsItemSelected(inputItem)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putIntegerArrayList(
            SAVE_INSTANCE_ADAPTER_STATE, adapterStateToIntegerArray(
                mAdapter!!.exportInGroupState()
            )
        )
        outState.putString(SAVE_INSTANCE_CURRENT_GROUP_NAME, mGroupNameText!!.getText().toString())
    }

    private fun updateLoyaltyCardList() {
        mAdapter!!.swapCursor(DBHelper.getLoyaltyCardCursor(mDatabase))

        if (mAdapter!!.getItemCount() == 0) {
            mCardList!!.setVisibility(View.GONE)
            noGroupCardsText!!.setVisibility(View.VISIBLE)
        } else {
            mCardList!!.setVisibility(View.VISIBLE)
            noGroupCardsText!!.setVisibility(View.GONE)
        }
    }

    private fun leaveWithoutSaving() {
        if (hasChanged()) {
            val builder: AlertDialog.Builder = MaterialAlertDialogBuilder(this@ManageGroupActivity)
            builder.setTitle(R.string.leaveWithoutSaveTitle)
            builder.setMessage(R.string.leaveWithoutSaveConfirmation)
            builder.setPositiveButton(
                R.string.confirm,
                DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int -> finish() })
            builder.setNegativeButton(
                R.string.cancel,
                DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int -> dialog!!.dismiss() })
            val dialog = builder.create()
            dialog.show()
        } else {
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        getOnBackPressedDispatcher().onBackPressed()
        return true
    }

    private fun hasChanged(): Boolean {
        return mAdapter!!.hasChanged() || mGroup!!._id != mGroupNameText!!.getText().toString()
            .trim { it <= ' ' }
    }

    override fun onRowLongClicked(inputPosition: Int) {
        mAdapter!!.toggleSelection(inputPosition)
    }

    override fun onRowClicked(inputPosition: Int) {
        mAdapter!!.toggleSelection(inputPosition)
    }
}
