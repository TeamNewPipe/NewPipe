package org.schabi.newpipe.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R

class EmptyFragment() : BaseFragment() {
    public override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                                     savedInstanceState: Bundle?): View? {
        val showMessage: Boolean = getArguments()!!.getBoolean(SHOW_MESSAGE)
        val view: View = inflater.inflate(R.layout.fragment_empty, container, false)
        view.findViewById<View>(R.id.empty_state_view).setVisibility(
                if (showMessage) View.VISIBLE else View.GONE)
        return view
    }

    companion object {
        private val SHOW_MESSAGE: String = "SHOW_MESSAGE"
        fun newInstance(showMessage: Boolean): EmptyFragment {
            val emptyFragment: EmptyFragment = EmptyFragment()
            val bundle: Bundle = Bundle(1)
            bundle.putBoolean(SHOW_MESSAGE, showMessage)
            emptyFragment.setArguments(bundle)
            return emptyFragment
        }
    }
}
